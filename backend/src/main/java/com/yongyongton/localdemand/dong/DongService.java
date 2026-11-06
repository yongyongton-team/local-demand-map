package com.yongyongton.localdemand.dong;

import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.request.ShopRequestRepository;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DongService {

    private final DongRepository dongRepository;
    private final GapRepository gapRepository;
    private final ShopRequestRepository shopRequestRepository;
    private final StoreRepository storeRepository;
    private final EntityManager em;
    private final ObjectMapper objectMapper;

    public DongService(
            DongRepository dongRepository,
            GapRepository gapRepository,
            ShopRequestRepository shopRequestRepository,
            StoreRepository storeRepository,
            EntityManager em,
            ObjectMapper objectMapper
    ) {
        this.dongRepository = dongRepository;
        this.gapRepository = gapRepository;
        this.shopRequestRepository = shopRequestRepository;
        this.storeRepository = storeRepository;
        this.em = em;
        this.objectMapper = objectMapper;
    }

    public List<Dong> getDongs() {
        return dongRepository.findAllByOrderByGuAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Gap> getMissingGaps(Long dongId) {

        if (!dongRepository.existsById(dongId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        return gapRepository.findByDongIdAndStatus(dongId, "MISSING")
                .stream()
                .sorted(
                        Comparator.<Gap>comparingLong(
                                        gap -> shopRequestRepository.countByDongIdAndCategoryId(
                                                dongId, gap.getCategoryId()
                                        )
                                ).reversed()
                                .thenComparing(Gap::getCategoryId)
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GapResponse> getGapResponses(Long dongId, Long userId) {

        return getMissingGaps(dongId).stream()
                .map(gap -> {

                    Category category = em.find(
                            Category.class, gap.getCategoryId()
                    );

                    if (category == null) {
                        throw new ApiException(ErrorCode.NOT_FOUND);
                    }

                    long requestCount =
                            shopRequestRepository.countByDongIdAndCategoryId(
                                    dongId, gap.getCategoryId()
                            );

                    boolean myRequested =
                            shopRequestRepository.existsByUserIdAndDongIdAndCategoryId(
                                    userId, dongId, gap.getCategoryId()
                            );

                    GapResponse.NearestMartResponse nearestMart = null;

                    if (category.isMartCheck()
                            && gap.getNearestMartId() != null
                            && gap.getNearestMartKm() != null) {

                        Mart mart = em.find(
                                Mart.class, gap.getNearestMartId()
                        );

                        if (mart != null) {
                            nearestMart = new GapResponse.NearestMartResponse(
                                    mart.getName(),
                                    gap.getNearestMartKm()
                            );
                        }
                    }

                    return new GapResponse(
                            gap.getCategoryId(),
                            category.getName(),
                            gap.getStatus(),
                            requestCount,
                            nearestMart,
                            myRequested
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DongMapResponse getDongMap(Long dongId, Long categoryId) {

        // 1. 행정동 확인
        Dong dong = dongRepository.findById(dongId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        // 업종 필터가 있다면 유효한 업종인지 확인
        if (categoryId != null && em.find(Category.class, categoryId) == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        // 2. 기존 가게 조회
        List<Store> stores = categoryId == null
                ? storeRepository.findByDongId(dongId)
                : storeRepository.findByDongIdAndCategoryId(dongId, categoryId);

        List<DongMapResponse.StoreInfo> storeInfos = stores.stream()
                .map(store -> new DongMapResponse.StoreInfo(
                        store.getId(),
                        store.getName(),
                        store.getCategoryId(),
                        store.getLat(),
                        store.getLon()
                ))
                .toList();

        // 3. 통계 모델 기반 빈 업종 조회
        List<DongMapResponse.GapInfo> gapInfos = getMissingGaps(dongId)
                .stream()
                .map(gap -> {
                    Category category = em.find(Category.class, gap.getCategoryId());
                    return new DongMapResponse.GapInfo(
                            category.getId(),
                            category.getName()
                    );
                })
                .toList();

        // 4. 동네의 업종별 주민 요청 수
        List<DongMapResponse.RequestInfo> requestInfos = em.createQuery(
                        "SELECT r.categoryId, COUNT(r) " +
                                "FROM ShopRequest r " +
                                "WHERE r.dongId = :dongId " +
                                "GROUP BY r.categoryId " +
                                "ORDER BY COUNT(r) DESC, r.categoryId ASC",
                        Object[].class
                )
                .setParameter("dongId", dongId)
                .getResultList()
                .stream()
                .map(row -> {
                    Long requestCategoryId = (Long) row[0];
                    Category category = em.find(Category.class, requestCategoryId);

                    return new DongMapResponse.RequestInfo(
                            requestCategoryId,
                            category.getName(),
                            (Long) row[1]
                    );
                })
                .toList();

        // 5. 대형마트 정보
        List<DongMapResponse.MartInfo> martInfos = em.createQuery(
                        "SELECT m FROM Mart m ORDER BY m.id",
                        Mart.class
                )
                .getResultList()
                .stream()
                .map(mart -> new DongMapResponse.MartInfo(
                        mart.getName(),
                        mart.getLat(),
                        mart.getLon()
                ))
                .toList();

        // 6. 지도 응답 생성
        return new DongMapResponse(
                new DongMapResponse.DongInfo(
                        dong.getId(),
                        dong.getName(),
                        new DongMapResponse.Center(
                                dong.getCenterLat(),
                                dong.getCenterLon()
                        ),
                        dong.getBoundary() == null
                                ? null
                                : objectMapper.readTree(dong.getBoundary())
                ),
                storeInfos,
                gapInfos,
                requestInfos,
                martInfos
        );
    }
}
