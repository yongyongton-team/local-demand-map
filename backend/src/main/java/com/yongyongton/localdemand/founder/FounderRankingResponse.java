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

    /**
     * 순위 한 줄. 줄 세우는 순서(FounderService.getRankings):
     * ① 공공데이터 부족 판정(SHORT → SOME_NEARBY → NEXT_DONG → 그 외) ② 주민 요청 수
     * ③ 부족이 뚜렷한 순(모델이 판정한 업종만) ④ score ⑤ 동 id.
     * 요청이 0건인 업종도 39개 동이 다 나와요.
     *
     * @param sameCategoryStoreCount 상가 데이터로 센 동 안 같은 업종 가게 수
     * @param score 요청 수 − 3 × 같은 업종 가게 수(식품 업종이고 대형마트 1.5km 안이면 반). 이제는 ④ 동점 정리에만 써요
     * @param publicData 리포트(GET /founder/report)의 publicData 와 같은 값·모양. 모델 결과가 없으면 null.
     *                   verdict 가 NOT_JUDGED 면 모델을 믿기 어려운 업종이라 숫자를 근거로 보여 주지 않아요
     */
    public record RankingItem(
            int rank,
            Long dongId,
            String name,
            long requestCount,
            long sameCategoryStoreCount,
            Double nearestMartKm,
            double score,
            FounderReportResponse.PublicData publicData
    ) {
    }
}
