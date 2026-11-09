package com.yongyongton.localdemand.founder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yongyongton.localdemand.ai.SummaryService.Summary;
import com.yongyongton.localdemand.dong.EstimateVerdict;
import com.yongyongton.localdemand.request.TimeSlot;
import java.util.List;

public record FounderReportResponse(
        DongInfo dong,
        CategoryInfo category,
        Stats stats,
        PublicData publicData,
        Summary explanation,
        List<Source> sources
) {

    public record DongInfo(
            Long id,
            String name,
            String gu
    ) {
    }

    public record CategoryInfo(
            Long id,
            String name
    ) {
    }

    public record Stats(
            long requestCount,
            long walkStoreCount,
            int walkRadiusM,
            NearestMart nearestMart,
            TimeSlot topTimeSlot,
            List<String> topConditions,
            Offers offers
    ) {
    }

    public record NearestMart(
            String name,
            Double km
    ) {
    }

    public record Offers(
            long done,
            long orders
    ) {
    }

    /**
     * 공공데이터 기대치 모델로 본 이 동네·업종 (V8). 모델 결과가 없는 동네면 publicData 가 null 이에요.
     * expectedCount 는 "인구 구성으로 보면 보통 몇 곳", dongStoreCount 는 "동 경계 안 실제 가게 수"예요.
     * verdict 가 NOT_JUDGED 면 모델을 믿기 어려운 업종이라 근거로 쓰지 않아요.
     */
    public record PublicData(
            Integer population,
            double expectedCount,
            int dongStoreCount,
            EstimateVerdict verdict,
            String verdictLabel
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(
            String name,
            String asOf
    ) {
    }
}
