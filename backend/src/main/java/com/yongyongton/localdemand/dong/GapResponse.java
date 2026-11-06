package com.yongyongton.localdemand.dong;

public record GapResponse(
        Long categoryId,
        String name,
        String status,
        long requestCount,
        NearestMartResponse nearestMart,
        boolean myRequested
) {
    public record NearestMartResponse(
            String name,
            double km
    ) {
    }
}
