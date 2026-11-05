package com.yongyongton.localdemand.owner;

import com.yongyongton.localdemand.ai.SummaryService.Summary;
import com.yongyongton.localdemand.request.RequestCondition;
import com.yongyongton.localdemand.request.TimeSlot;
import java.util.List;

/** 사장님 화면 응답 (명세 5-5). */
public final class OwnerDtos {

    private OwnerDtos() {
    }

    public record IdName(Long id, String name) {
    }

    public record LatLon(double lat, double lon) {
    }

    /**
     * GET /owner/demand 응답 (화면 4). 반경으로 보면 radiusKm 에 숫자·gu 는 null,
     * 구 전체로 보면(?gu=기흥구) radiusKm 은 null·gu 에 구 이름.
     */
    public record DemandResponse(ShopInfo shop, Double radiusKm, String gu, List<Area> areas) {
    }

    /** 내 가게. dong 은 명세 예시대로 이름 문자열이에요(용인시 밖이면 null). */
    public record ShopInfo(String shopName, IdName category, String dong, Double lat, Double lon) {
    }

    /**
     * 요청이 있는 동네 한 줄. distanceKm 는 내 가게에서 동 중심까지 직선거리(소수 첫째 자리),
     * topTimeSlot 은 가장 많이 고른 시간대, sameCategoryStoreCount 는 그 동 안의 같은 업종 가게 수(상가정보).
     */
    public record Area(Long dongId, String name, long requestCount, double distanceKm, TimeSlot topTimeSlot,
                       long sameCategoryStoreCount, LatLon center) {
    }

    /** GET /owner/demand/{dongId} 응답 (화면 5). 숫자는 서버가 세고 summary 문장만 AI가 써요. */
    public record DetailResponse(IdName dong, IdName category, long requestCount, long sameCategoryStoreCount,
                                 List<ConditionCount> conditions, List<SlotCount> timeSlots, Summary summary) {
    }

    /** 조건별 요청 수. 많은 순, 0인 조건은 빼요. */
    public record ConditionCount(RequestCondition code, String label, long count) {
    }

    /** 시간대별 요청 수. ratio = 그 시간대를 고른 요청 수 ÷ 전체 요청 수(소수 둘째 자리). 합이 1이 아닐 수 있어요. */
    public record SlotCount(TimeSlot slot, long count, double ratio) {
    }
}
