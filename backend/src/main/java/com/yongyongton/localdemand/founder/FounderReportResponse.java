package com.yongyongton.localdemand.founder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yongyongton.localdemand.ai.SummaryService.Summary;
import com.yongyongton.localdemand.request.TimeSlot;
import java.util.List;

public record FounderReportResponse(
        DongInfo dong,
        CategoryInfo category,
        Stats stats,
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(
            String name,
            String asOf
    ) {
    }
}
