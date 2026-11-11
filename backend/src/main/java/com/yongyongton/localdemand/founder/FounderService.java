package com.yongyongton.localdemand.founder;

import com.yongyongton.localdemand.ai.SummaryKind;
import com.yongyongton.localdemand.ai.SummaryService;
import com.yongyongton.localdemand.ai.SummaryService.Summary;
import com.yongyongton.localdemand.common.Geo;
import com.yongyongton.localdemand.common.KoreanText;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.dong.CategoryRepository;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.dong.DongCategoryEstimate;
import com.yongyongton.localdemand.dong.DongCategoryEstimateRepository;
import com.yongyongton.localdemand.dong.DongRepository;
import com.yongyongton.localdemand.dong.Mart;
import com.yongyongton.localdemand.dong.Store;
import com.yongyongton.localdemand.offer.OfferStatus;
import com.yongyongton.localdemand.offer.OrderStatus;
import com.yongyongton.localdemand.request.RequestCondition;
import com.yongyongton.localdemand.request.ShopRequest;
import com.yongyongton.localdemand.request.TimeSlot;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FounderService {

    private static final double WALK_RADIUS_KM = 1.0;
    private static final int TOP_CONDITIONS = 3;

    private static final List<FounderReportResponse.Source> SOURCES = List.of(
            new FounderReportResponse.Source(
                    "소상공인시장진흥공단 상가(상권)정보", null
            ),
            new FounderReportResponse.Source(
                    "행정안전부 행정동별 주민등록 인구·세대", "2026-09"
            ),
            new FounderReportResponse.Source(
                    "가게 수 기대치 모델(경기도 행정동 565곳으로 학습, 이 서비스)", null
            ),
            new FounderReportResponse.Source(
                    "경기도 용인시 대규모 점포 현황", "2026-05-07"
            ),
            new FounderReportResponse.Source(
                    "통계청 SGIS 행정동 경계", null
            ),
            new FounderReportResponse.Source(
                    "주민 요청(이 서비스)", null
            )
    );

    private final DongRepository dongRepository;
    private final CategoryRepository categoryRepository;
    private final DongCategoryEstimateRepository estimateRepository;
    private final EntityManager em;
    private final SummaryService summaryService;
    private final TransactionTemplate readOnly;

    public FounderService(
            DongRepository dongRepository,
            CategoryRepository categoryRepository,
            DongCategoryEstimateRepository estimateRepository,
            EntityManager em,
            SummaryService summaryService,
            PlatformTransactionManager transactionManager
    ) {
        this.dongRepository = dongRepository;
        this.categoryRepository = categoryRepository;
        this.estimateRepository = estimateRepository;
        this.em = em;
        this.summaryService = summaryService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    // =====================================================
    // 1. 예비 창업자 입지 순위 API
    // GET /founder/rankings
    // 요청이 없는 업종도 공공데이터로 가게가 부족한 동네를 먼저 보여 줘요
    // =====================================================

    @Transactional(readOnly = true)
    public FounderRankingResponse getRankings(Long categoryId, int limit) {

        // 1. 입력값 확인
        if (limit < 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        // 2. 전체 행정동 조회
        List<Dong> dongs = dongRepository.findAllByOrderByGuAscNameAsc();

        // 3. 동별 주민 요청 수 조회
        Map<Long, Long> requestCounts = new HashMap<>();

        List<Object[]> requestRows = em.createQuery(
                        "SELECT r.dongId, COUNT(r) " +
                                "FROM ShopRequest r " +
                                "WHERE r.categoryId = :categoryId " +
                                "GROUP BY r.dongId",
                        Object[].class
                )
                .setParameter("categoryId", categoryId)
                .getResultList();

        for (Object[] row : requestRows) {
            requestCounts.put((Long) row[0], (Long) row[1]);
        }

        // 4. 동별 같은 업종 점포 수 조회
        Map<Long, Long> storeCounts = new HashMap<>();

        List<Object[]> storeRows = em.createQuery(
                        "SELECT s.dongId, COUNT(s) " +
                                "FROM Store s " +
                                "WHERE s.categoryId = :categoryId " +
                                "AND s.dongId IS NOT NULL " +
                                "GROUP BY s.dongId",
                        Object[].class
                )
                .setParameter("categoryId", categoryId)
                .getResultList();

        for (Object[] row : storeRows) {
            storeCounts.put((Long) row[0], (Long) row[1]);
        }

        // 5. 대형마트 조회
        List<Mart> marts = em.createQuery(
                "SELECT m FROM Mart m",
                Mart.class
        ).getResultList();

        // 6. 공공데이터 기대치 모델 결과 (V8). 업종마다 39개 동이 다 있어요
        Map<Long, DongCategoryEstimate> estimates = new HashMap<>();

        for (DongCategoryEstimate estimate : estimateRepository.findByCategoryId(categoryId)) {
            estimates.put(estimate.getDongId(), estimate);
        }

        // 7. 행정동별 값 모으기
        List<Candidate> candidates = new ArrayList<>();

        for (Dong dong : dongs) {

            long requestCount =
                    requestCounts.getOrDefault(dong.getId(), 0L);

            long storeCount =
                    storeCounts.getOrDefault(dong.getId(), 0L);

            // 가장 가까운 대형마트까지의 직선거리
            Double nearestMartKm = null;

            for (Mart mart : marts) {
                double distance = Geo.distanceKm(
                        dong.getCenterLat(),
                        dong.getCenterLon(),
                        mart.getLat(),
                        mart.getLon()
                );

                if (nearestMartKm == null || distance < nearestMartKm) {
                    nearestMartKm = distance;
                }
            }

            // 예전 점수. 이제는 동점 정리에만 써요
            double score = requestCount - 3.0 * storeCount;

            // 식품 업종 + 대형마트 1.5km 이내
            if (score > 0
                    && category.isMartCheck()
                    && nearestMartKm != null
                    && nearestMartKm <= 1.5) {
                score *= 0.5;
            }

            candidates.add(new Candidate(
                    dong,
                    requestCount,
                    storeCount,
                    nearestMartKm == null ? null : Geo.round1(nearestMartKm),
                    score,
                    estimates.get(dong.getId())
            ));
        }

        // 8. 데이터 부족 판정 → 요청 수 → 부족이 뚜렷한 순 → 예전 점수 → 동 id
        candidates.sort(RANKING_ORDER);

        // 9. limit 적용 및 순위 부여
        List<FounderRankingResponse.RankingItem> items =
                new ArrayList<>();

        int size = Math.min(limit, candidates.size());

        for (int i = 0; i < size; i++) {
            Candidate c = candidates.get(i);

            items.add(new FounderRankingResponse.RankingItem(
                    i + 1,
                    c.dong().getId(),
                    c.dong().getName(),
                    c.requestCount(),
                    c.storeCount(),
                    c.nearestMartKm(),
                    c.score(),
                    toPublicData(c.dong(), c.estimate())
            ));
        }

        return new FounderRankingResponse(
                new FounderRankingResponse.CategoryInfo(
                        category.getId(),
                        category.getName()
                ),
                items
        );
    }

    /** 순위를 매기기 전 동 하나의 값. estimate 는 모델 결과가 없으면 null 이에요. */
    private record Candidate(
            Dong dong,
            long requestCount,
            long storeCount,
            Double nearestMartKm,
            double score,
            DongCategoryEstimate estimate
    ) {

        /** 공공데이터 부족 판정 단계. 클수록 위예요. 모델을 믿기 어려운 업종(NOT_JUDGED)은 0이에요 */
        int shortageLevel() {
            if (estimate == null) {
                return 0;
            }
            return switch (estimate.getVerdict()) {
                case SHORT -> 3;
                case SOME_NEARBY -> 2;
                case NEXT_DONG -> 1;
                case NOT_SHORT, NOT_JUDGED -> 0;
            };
        }

        /**
         * 기대치가 맞다면 실제 가게 수가 이만큼 적을 확률(prob_fewer). 작을수록 부족이 뚜렷해요.
         * 기대치가 작은 동네에서 0곳인 것을 "부족"으로 올리지 않으려고 비율 대신 이 값을 써요.
         * 판정하지 않은 업종은 비교하지 않아요(모두 1).
         */
        double shortageProbability() {
            return estimate != null && estimate.getVerdict().judged()
                    ? estimate.getProbFewer()
                    : 1.0;
        }
    }

    /** 데이터 부족 판정 → 주민 요청 수 → 부족이 뚜렷한 순 → 예전 점수 → 동 id */
    private static final Comparator<Candidate> RANKING_ORDER = Comparator
            .<Candidate>comparingInt(Candidate::shortageLevel).reversed()
            .thenComparing(Comparator.<Candidate>comparingLong(Candidate::requestCount).reversed())
            .thenComparingDouble(Candidate::shortageProbability)
            .thenComparing(Comparator.<Candidate>comparingDouble(Candidate::score).reversed())
            .thenComparing(c -> c.dong().getId());

    /** 순위와 리포트가 같은 모양으로 쓰는 공공데이터 근거. 모델 결과가 없으면 null */
    private static FounderReportResponse.PublicData toPublicData(Dong dong, DongCategoryEstimate estimate) {
        if (estimate == null) {
            return null;
        }
        return new FounderReportResponse.PublicData(
                dong.getPopulation(),
                estimate.getExpectedCount(),
                estimate.getDongStoreCount(),
                estimate.getVerdict(),
                estimate.getVerdict().label()
        );
    }

    // =====================================================
    // 2. 예비 창업자 동네 리포트 API
    // GET /founder/report
    // =====================================================

    public FounderReportResponse getReport(Long dongId, Long categoryId) {

        // 1. DB에서 통계 계산
        ReportData data = readOnly.execute(
                status -> loadReportData(dongId, categoryId)
        );

        FounderReportResponse.Stats stats = data.stats();
        FounderReportResponse.PublicData publicData = data.publicData();

        // 공공데이터 모델을 근거로 쓸 수 있는지. 모델을 믿기 어려운 업종(NOT_JUDGED)은 쓰지 않아요
        boolean modelEvidence = publicData != null
                && publicData.verdict().judged();
        long expectedStores = publicData == null
                ? 0
                : Math.round(publicData.expectedCount());

        // 2. AI에 전달할 사실 데이터 구성
        Map<String, Object> facts = new LinkedHashMap<>();

        facts.put("동네", data.dong().name());
        facts.put("업종", data.category().name());

        if (modelEvidence) {
            if (publicData.population() != null) {
                facts.put("주민등록 인구", publicData.population());
            }
            facts.put("인구 구성으로 본 보통 가게 수", expectedStores);
            facts.put("동 안 실제 가게 수", publicData.dongStoreCount());
            facts.put("공공데이터 판정", publicData.verdictLabel());
        }

        facts.put("주민 요청 수", stats.requestCount());
        facts.put("직선거리 반경(km)", WALK_RADIUS_KM);
        facts.put("반경 내 같은 업종 점포 수", stats.walkStoreCount());

        if (stats.nearestMart() != null) {
            facts.put("가장 가까운 대형마트", stats.nearestMart().name());
            facts.put("대형마트 거리(km)", stats.nearestMart().km());
        }

        facts.put(
                "가장 많이 선택한 시간대",
                stats.topTimeSlot() == null
                        ? "없음"
                        : stats.topTimeSlot().label()
        );

        facts.put("많이 선택한 조건", stats.topConditions());

        // 선주문 숫자는 있을 때만 넣어요. 0을 넣으면 AI가 "수령 수량은 0이에요"처럼 어색하게 써요
        if (stats.offers().done() > 0 || stats.offers().orders() > 0) {
            facts.put("수령 완료된 제안 수", stats.offers().done());
            facts.put("실제 수령 수량", stats.offers().orders());
        }

        // 3. AI를 사용할 수 없을 때의 기본 설명
        StringBuilder template = new StringBuilder();

        if (modelEvidence) {
            template.append(KoreanText.josa(
                    data.dong().name(), "은", "는"
            ));
            if (publicData.population() != null) {
                template.append(" 주민등록 인구 ")
                        .append(String.format("%,d", publicData.population()))
                        .append("명의 나이·가구 구성으로 보면 ");
            } else {
                template.append(" 인구 구성으로 보면 ");
            }
            template.append(KoreanText.josa(
                            data.category().name(), "이", "가"
                    ))
                    .append(" 보통 ").append(expectedStores)
                    .append("곳 있을 만한데, 동 안에는 ")
                    .append(publicData.dongStoreCount()).append("곳이에요. ")
                    .append(publicData.verdictLabel()).append(". ");
        }

        template.append(data.dong().name()).append("에서 ")
                .append(KoreanText.josa(
                        data.category().name(), "을", "를"
                ))
                .append(" 원하는 주민 요청은 ")
                .append(stats.requestCount()).append("건이에요.")
                .append(" 동 중심에서 직선거리 1km 이내 같은 업종 점포는 ")
                .append(stats.walkStoreCount()).append("곳이에요.");

        if (stats.nearestMart() != null) {
            template.append(" 가장 가까운 대형마트(")
                    .append(stats.nearestMart().name())
                    .append(")는 ")
                    .append(stats.nearestMart().km())
                    .append("km 떨어져 있어요.");
        }

        if (stats.topTimeSlot() != null) {
            template.append(" 가장 많이 선택한 시간대는 ")
                    .append(stats.topTimeSlot().label())
                    .append("이에요.");
        }

        // 4. DB 트랜잭션 밖에서 AI 요약 생성
        // 주민 요청이 없어도 공공데이터 근거가 있으면 AI가 설명해요. 둘 다 없으면 기본 문장
        boolean hasEvidence = stats.requestCount() > 0 || modelEvidence;
        Summary explanation = !hasEvidence
                ? Summary.template(template.toString())
                : summaryService.write(
                SummaryKind.FOUNDER_REPORT,
                dongId,
                categoryId,
                facts,
                template.toString()
        );

        // 5. 최종 리포트 응답
        return new FounderReportResponse(
                data.dong(),
                data.category(),
                stats,
                publicData,
                explanation,
                SOURCES
        );
    }

    // =====================================================
    // 3. 리포트 통계 조회
    // =====================================================

    private record ReportData(
            FounderReportResponse.DongInfo dong,
            FounderReportResponse.CategoryInfo category,
            FounderReportResponse.Stats stats,
            FounderReportResponse.PublicData publicData
    ) {
    }

    private ReportData loadReportData(Long dongId, Long categoryId) {

        // 1. 행정동 및 업종 확인
        Dong dong = dongRepository.findById(dongId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        // 2. 주민 요청 조회
        List<ShopRequest> requests = em.createQuery(
                        "SELECT r FROM ShopRequest r " +
                                "WHERE r.dongId = :dongId " +
                                "AND r.categoryId = :categoryId",
                        ShopRequest.class
                )
                .setParameter("dongId", dongId)
                .setParameter("categoryId", categoryId)
                .getResultList();

        // 3. 선호 시간대 및 조건 집계
        Map<TimeSlot, Long> timeCounts =
                new EnumMap<>(TimeSlot.class);

        Map<RequestCondition, Long> conditionCounts =
                new EnumMap<>(RequestCondition.class);

        for (ShopRequest request : requests) {

            request.getTimeSlots().forEach(
                    slot -> timeCounts.merge(slot, 1L, Long::sum)
            );

            request.getConditions().forEach(
                    condition -> conditionCounts.merge(
                            condition, 1L, Long::sum
                    )
            );
        }

        // 가장 많이 선택한 시간대
        TimeSlot topTimeSlot = timeCounts.entrySet().stream()
                .sorted(
                        Comparator
                                .<Map.Entry<TimeSlot, Long>>comparingLong(
                                        Map.Entry::getValue
                                )
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        // 많이 선택한 조건 상위 3개
        List<String> topConditions = conditionCounts.entrySet().stream()
                .sorted(
                        Comparator
                                .<Map.Entry<RequestCondition, Long>>comparingLong(
                                        Map.Entry::getValue
                                )
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .limit(TOP_CONDITIONS)
                .map(entry -> entry.getKey().label())
                .toList();

        // 4. 동 중심 반경 1km 이내 같은 업종 점포 수
        long walkStoreCount = em.createQuery(
                        "SELECT s FROM Store s " +
                                "WHERE s.categoryId = :categoryId",
                        Store.class
                )
                .setParameter("categoryId", categoryId)
                .getResultStream()
                .filter(store -> Geo.distanceKm(
                        dong.getCenterLat(),
                        dong.getCenterLon(),
                        store.getLat(),
                        store.getLon()
                ) <= WALK_RADIUS_KM)
                .count();

        // 5. 가장 가까운 대형마트 찾기
        Mart nearestMart = null;
        double nearestKm = Double.POSITIVE_INFINITY;

        List<Mart> marts = em.createQuery(
                "SELECT m FROM Mart m",
                Mart.class
        ).getResultList();

        for (Mart mart : marts) {

            double distance = Geo.distanceKm(
                    dong.getCenterLat(),
                    dong.getCenterLon(),
                    mart.getLat(),
                    mart.getLon()
            );

            if (distance < nearestKm) {
                nearestKm = distance;
                nearestMart = mart;
            }
        }

        FounderReportResponse.NearestMart nearest =
                nearestMart == null
                        ? null
                        : new FounderReportResponse.NearestMart(
                        nearestMart.getName(),
                        Geo.round1(nearestKm)
                );

        // 6. 수령 완료된 제안 수
        Long done = em.createQuery(
                        "SELECT COUNT(o) FROM Offer o " +
                                "WHERE o.dongId = :dongId " +
                                "AND o.categoryId = :categoryId " +
                                "AND o.status = :status",
                        Long.class
                )
                .setParameter("dongId", dongId)
                .setParameter("categoryId", categoryId)
                .setParameter("status", OfferStatus.DONE)
                .getSingleResult();

        // 7. 실제 수령된 선주문 수량 합계
        Long orders = em.createQuery(
                        "SELECT SUM(p.quantity) FROM PreOrder p " +
                                "JOIN p.offer o " +
                                "WHERE o.dongId = :dongId " +
                                "AND o.categoryId = :categoryId " +
                                "AND o.status = :done " +
                                "AND p.status = :pickedUp",
                        Long.class
                )
                .setParameter("dongId", dongId)
                .setParameter("categoryId", categoryId)
                .setParameter("done", OfferStatus.DONE)
                .setParameter("pickedUp", OrderStatus.PICKED_UP)
                .getSingleResult();

        // 8. 공공데이터 기대치 모델 결과 (V8). 모델 결과가 없는 동네면 null
        FounderReportResponse.PublicData publicData = toPublicData(
                dong,
                estimateRepository.findByDongIdAndCategoryId(dongId, categoryId).orElse(null)
        );

        // 9. 최종 통계 DTO 구성
        return new ReportData(
                new FounderReportResponse.DongInfo(
                        dong.getId(),
                        dong.getName(),
                        dong.getGu()
                ),
                new FounderReportResponse.CategoryInfo(
                        category.getId(),
                        category.getName()
                ),
                new FounderReportResponse.Stats(
                        requests.size(),
                        walkStoreCount,
                        1000,
                        nearest,
                        topTimeSlot,
                        topConditions,
                        new FounderReportResponse.Offers(
                                done,
                                orders == null ? 0 : orders
                        )
                ),
                publicData
        );
    }
}
