package com.yongyongton.localdemand.founder;

import java.util.List;

public record FounderRankingResponse(
        CategoryInfo category,
        List<RankingItem> items
) {
    public record CategoryInfo(
            Long id,
            String name
    ) {
    }

    public record RankingItem(
            int rank,
            Long dongId,
            String name,
            long requestCount,
            long sameCategoryStoreCount,
            Double nearestMartKm,
            double score
    ) {
    }
}
