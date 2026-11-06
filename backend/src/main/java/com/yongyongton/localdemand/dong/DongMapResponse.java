package com.yongyongton.localdemand.dong;

import java.util.List;
import tools.jackson.databind.JsonNode;

public record DongMapResponse(
        DongInfo dong,
        List<StoreInfo> stores,
        List<GapInfo> gaps,
        List<RequestInfo> requests,
        List<MartInfo> marts
) {
    public record DongInfo(
            Long id,
            String name,
            Center center,
            JsonNode boundary
    ) {
    }

    public record Center(
            double lat,
            double lon
    ) {
    }

    public record StoreInfo(
            Long id,
            String name,
            Long categoryId,
            double lat,
            double lon
    ) {
    }

    public record GapInfo(
            Long categoryId,
            String name
    ) {
    }

    public record RequestInfo(
            Long categoryId,
            String name,
            long count
    ) {
    }

    public record MartInfo(
            String name,
            double lat,
            double lon
    ) {
    }
}
