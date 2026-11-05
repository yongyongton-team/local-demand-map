package com.yongyongton.localdemand.owner;

import com.yongyongton.localdemand.ai.SummaryKind;
import com.yongyongton.localdemand.ai.SummaryService;
import com.yongyongton.localdemand.ai.SummaryService.Summary;
import com.yongyongton.localdemand.auth.AppUser;
import com.yongyongton.localdemand.auth.CurrentUser;
import com.yongyongton.localdemand.auth.OwnerShop;
import com.yongyongton.localdemand.auth.OwnerShopRepository;
import com.yongyongton.localdemand.auth.Role;
import com.yongyongton.localdemand.common.Geo;
import com.yongyongton.localdemand.common.KoreanText;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.owner.OwnerDtos.Area;
import com.yongyongton.localdemand.owner.OwnerDtos.ConditionCount;
import com.yongyongton.localdemand.owner.OwnerDtos.DemandResponse;
import com.yongyongton.localdemand.owner.OwnerDtos.DetailResponse;
import com.yongyongton.localdemand.owner.OwnerDtos.IdName;
import com.yongyongton.localdemand.owner.OwnerDtos.LatLon;
import com.yongyongton.localdemand.owner.OwnerDtos.ShopInfo;
import com.yongyongton.localdemand.owner.OwnerDtos.SlotCount;
import com.yongyongton.localdemand.request.RequestCondition;
import com.yongyongton.localdemand.request.ShopRequest;
import com.yongyongton.localdemand.request.TimeSlot;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사장님 화면 (명세 5-5). 숫자는 모두 여기서 세고, 화면 5의 summary 문장만 AI가 써요.
 * 화면 5는 AI를 기다리는 동안 DB 연결을 잡지 않게 "숫자 세기(읽기 트랜잭션) → 문장(트랜잭션 밖)" 순서로 해요.
 */
@Service
public class OwnerDemandService {

    static final double DEFAULT_RADIUS_KM = 3.0;
    static final double MAX_RADIUS_KM = 20.0;
    /** AI에 넣는 조건 수(많은 순). */
    private static final int TOP_CONDITIONS = 3;

    private final CurrentUser currentUser;
    private final OwnerShopRepository ownerShopRepository;
    private final SummaryService summaryService;
    private final EntityManager em;
    private final TransactionTemplate readOnly;

    public OwnerDemandService(CurrentUser currentUser, OwnerShopRepository ownerShopRepository,
                              SummaryService summaryService, EntityManager em,
                              PlatformTransactionManager transactionManager) {
        this.currentUser = currentUser;
        this.ownerShopRepository = ownerShopRepository;
        this.summaryService = summaryService;
        this.em = em;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * GET /owner/demand (화면 4). 내 가게 업종의 요청이 있는 동네를 요청 많은 순으로.
     * ?gu= 가 있으면 그 구 전체, 없으면 내 가게에서 radiusKm(기본 3km) 안.
     * 사장님만(아니면 403), 가게를 등록하지 않았으면 404.
     */
    public DemandResponse demand(Jwt jwt, Double radiusKm, String gu) {
        AppUser owner = currentUser.require(jwt, Role.OWNER);
        boolean byGu = gu != null && !gu.isBlank();
        Double radius = byGu ? null : (radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm);
        if (radius != null && (radius <= 0 || radius > MAX_RADIUS_KM)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "radiusKm 은 0보다 크고 20 이하예요.");
        }
        return readOnly.execute(status -> {
            OwnerShop shop = shopOf(owner);
            Category category = em.find(Category.class, shop.getCategoryId());
            Dong shopDong = shop.getDongId() == null ? null : em.find(Dong.class, shop.getDongId());
            ShopInfo shopInfo = new ShopInfo(shop.getShopName(), idName(category, shop.getCategoryId()),
                    shopDong == null ? null : shopDong.getName(), shop.getLat(), shop.getLon());

            // 동네별 요청 수와 시간대 수
            Map<Long, Long> requestCounts = new HashMap<>();
            Map<Long, Map<TimeSlot, Long>> slotCounts = new HashMap<>();
            for (ShopRequest r : em.createQuery(
                            "SELECT r FROM ShopRequest r WHERE r.categoryId = :categoryId", ShopRequest.class)
                    .setParameter("categoryId", shop.getCategoryId())
                    .getResultList()) {
                requestCounts.merge(r.getDongId(), 1L, Long::sum);
                Map<TimeSlot, Long> slots = slotCounts.computeIfAbsent(r.getDongId(), k -> new EnumMap<>(TimeSlot.class));
                r.getTimeSlots().forEach(s -> slots.merge(s, 1L, Long::sum));
            }
            if (requestCounts.isEmpty()) {
                return new DemandResponse(shopInfo, radius, byGu ? gu.trim() : null, List.of());
            }

            Map<Long, Long> storeCounts = storeCountsByDong(shop.getCategoryId(), requestCounts.keySet());
            List<Area> areas = new ArrayList<>();
            for (Object[] d : em.createQuery(
                            "SELECT d.id, d.name, d.gu, d.centerLat, d.centerLon FROM Dong d WHERE d.id IN :ids",
                            Object[].class)
                    .setParameter("ids", requestCounts.keySet())
                    .getResultList()) {
                Long dongId = (Long) d[0];
                String dongGu = (String) d[2];
                double lat = (Double) d[3];
                double lon = (Double) d[4];
                double distance = Geo.distanceKm(shop.getLat(), shop.getLon(), lat, lon);
                boolean inside = byGu ? dongGu.equals(gu.trim()) : distance <= radius;
                if (inside) {
                    areas.add(new Area(dongId, (String) d[1], requestCounts.get(dongId), Geo.round1(distance),
                            topSlot(slotCounts.get(dongId)), storeCounts.getOrDefault(dongId, 0L),
                            new LatLon(lat, lon)));
                }
            }
            areas.sort(Comparator.comparingLong(Area::requestCount).reversed()
                    .thenComparingDouble(Area::distanceKm)
                    .thenComparing(Area::dongId));
            return new DemandResponse(shopInfo, radius, byGu ? gu.trim() : null, areas);
        });
    }

    /**
     * GET /owner/demand/{dongId} (화면 5). ?categoryId= 를 빼면 내 가게 업종.
     * 사장님만(아니면 403), 없는 동·업종이면 404, 업종을 빼고 불렀는데 가게가 없으면 404.
     */
    public DetailResponse detail(Jwt jwt, Long dongId, Long categoryId) {
        AppUser owner = currentUser.require(jwt, Role.OWNER);
        Stats stats = readOnly.execute(status -> countStats(owner, dongId, categoryId));

        Summary summary;
        if (stats.requestCount() == 0) {
            summary = Summary.template(stats.dongName() + "에는 아직 "
                    + KoreanText.josa(stats.categoryName(), "을", "를") + " 원하는 요청이 없어요.");
        } else {
            summary = summaryService.write(SummaryKind.OWNER_DEMAND, stats.dongId(), stats.categoryId(),
                    facts(stats), template(stats));
        }
        return new DetailResponse(
                new IdName(stats.dongId(), stats.dongName()),
                new IdName(stats.categoryId(), stats.categoryName()),
                stats.requestCount(), stats.storeCount(), stats.conditions(), stats.timeSlots(), summary);
    }

    /** 화면 5의 숫자들. */
    private record Stats(Long dongId, String dongName, Long categoryId, String categoryName, long requestCount,
                         long storeCount, List<ConditionCount> conditions, List<SlotCount> timeSlots) {
    }

    private Stats countStats(AppUser owner, Long dongId, Long categoryId) {
        Long targetCategoryId = categoryId != null ? categoryId : shopOf(owner).getCategoryId();
        Dong dong = em.find(Dong.class, dongId);
        if (dong == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "없는 동이에요.");
        }
        Category category = em.find(Category.class, targetCategoryId);
        if (category == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "없는 업종이에요.");
        }

        List<ShopRequest> requests = em.createQuery(
                        "SELECT r FROM ShopRequest r WHERE r.dongId = :dongId AND r.categoryId = :categoryId",
                        ShopRequest.class)
                .setParameter("dongId", dongId)
                .setParameter("categoryId", targetCategoryId)
                .getResultList();
        Map<RequestCondition, Long> conditionCounts = new EnumMap<>(RequestCondition.class);
        Map<TimeSlot, Long> slotCounts = new EnumMap<>(TimeSlot.class);
        for (ShopRequest r : requests) {
            r.getConditions().forEach(c -> conditionCounts.merge(c, 1L, Long::sum));
            r.getTimeSlots().forEach(s -> slotCounts.merge(s, 1L, Long::sum));
        }
        long total = requests.size();

        // 많은 순, 같으면 목록(enum) 순서
        List<ConditionCount> conditions = conditionCounts.entrySet().stream()
                .sorted(Map.Entry.<RequestCondition, Long>comparingByValue().reversed())
                .map(e -> new ConditionCount(e.getKey(), e.getKey().label(), e.getValue()))
                .toList();
        List<SlotCount> timeSlots = slotCounts.entrySet().stream()
                .sorted(Map.Entry.<TimeSlot, Long>comparingByValue().reversed())
                .map(e -> new SlotCount(e.getKey(), e.getValue(), Math.round(e.getValue() * 100.0 / total) / 100.0))
                .toList();

        long storeCount = storeCountsByDong(targetCategoryId, List.of(dongId)).getOrDefault(dongId, 0L);
        return new Stats(dong.getId(), dong.getName(), category.getId(), category.getName(), total, storeCount,
                conditions, timeSlots);
    }

    /** AI에 넣는 자료. 서버가 센 숫자와 이름만 넣어요(주민 문장은 넣지 않아요). */
    private static Map<String, Object> facts(Stats s) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("동네", s.dongName());
        facts.put("업종", s.categoryName());
        facts.put("요청한 주민 수", s.requestCount());
        facts.put("동네 안 같은 업종 가게 수", s.storeCount());
        facts.put("갈 수 있는 때", s.timeSlots().stream()
                .map(t -> Map.of("때", t.slot().label(), "요청 수", t.count()))
                .toList());
        facts.put("바라는 점", s.conditions().stream().limit(TOP_CONDITIONS)
                .map(c -> Map.of("조건", c.label(), "요청 수", c.count()))
                .toList());
        return facts;
    }

    /** AI를 못 쓸 때 기본 문장. 예: 보정동에는 과일·채소 가게를 원하는 주민이 14명 있어요. 주말에 … */
    private static String template(Stats s) {
        StringBuilder text = new StringBuilder()
                .append(s.dongName()).append("에는 ")
                .append(KoreanText.josa(s.categoryName(), "을", "를"))
                .append(" 원하는 주민이 ").append(s.requestCount()).append("명 있어요.");
        if (!s.timeSlots().isEmpty()) {
            SlotCount top = s.timeSlots().get(0);
            text.append(" ").append(top.slot().label()).append("에 갈 수 있다는 요청이 가장 많아요(")
                    .append(top.count()).append("명).");
        }
        if (!s.conditions().isEmpty()) {
            ConditionCount top = s.conditions().get(0);
            text.append(" 가장 많이 바라는 점은 '").append(top.label()).append("'이에요(")
                    .append(top.count()).append("명).");
        }
        return text.toString();
    }

    private OwnerShop shopOf(AppUser owner) {
        return ownerShopRepository.findByUserId(owner.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "가게를 먼저 등록해 주세요."));
    }

    /** 동마다 같은 업종 가게 수(상가정보 store, 동 경계 기준). */
    private Map<Long, Long> storeCountsByDong(Long categoryId, Collection<Long> dongIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : em.createQuery(
                        "SELECT s.dongId, COUNT(s) FROM Store s WHERE s.categoryId = :categoryId AND s.dongId IN :ids"
                                + " GROUP BY s.dongId", Object[].class)
                .setParameter("categoryId", categoryId)
                .setParameter("ids", dongIds)
                .getResultList()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** 가장 많이 고른 시간대. 같으면 목록(enum) 순서로 앞의 것, 없으면 null. */
    private static TimeSlot topSlot(Map<TimeSlot, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return null;
        }
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<TimeSlot, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static IdName idName(Category category, Long categoryId) {
        return category == null ? new IdName(categoryId, null) : new IdName(category.getId(), category.getName());
    }
}
