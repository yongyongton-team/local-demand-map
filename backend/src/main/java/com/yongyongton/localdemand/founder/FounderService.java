package com.yongyongton.localdemand.founder;

import com.yongyongton.localdemand.common.Geo;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.dong.Category;
import com.yongyongton.localdemand.dong.CategoryRepository;
import com.yongyongton.localdemand.dong.Dong;
import com.yongyongton.localdemand.dong.DongRepository;
import com.yongyongton.localdemand.dong.Mart;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FounderService {

    private final DongRepository dongRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager em;

    public FounderService(
            DongRepository dongRepository,
            CategoryRepository categoryRepository,
            EntityManager em
    ) {
        this.dongRepository = dongRepository;
        this.categoryRepository = categoryRepository;
        this.em = em;
    }

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

        // 6. 행정동별 점수 계산
        List<FounderRankingResponse.RankingItem> candidates =
                new ArrayList<>();

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

            // 기본 점수
            double score = requestCount - 3.0 * storeCount;

            // 식품 업종 + 대형마트 1.5km 이내
            if (category.isMartCheck()
                    && nearestMartKm != null
                    && nearestMartKm <= 1.5) {
                score *= 0.5;
            }

            candidates.add(
                    new FounderRankingResponse.RankingItem(
                            0,
                            dong.getId(),
                            dong.getName(),
                            requestCount,
                            storeCount,
                            nearestMartKm == null
                                    ? null
                                    : Geo.round1(nearestMartKm),
                            score
                    )
            );
        }

        // 7. 점수 내림차순 정렬
        candidates.sort(
                Comparator
                        .<FounderRankingResponse.RankingItem>comparingDouble(
                                FounderRankingResponse.RankingItem::score
                        )
                        .reversed()
                        .thenComparing(
                                FounderRankingResponse.RankingItem::dongId
                        )
        );

        // 8. limit 적용 및 순위 부여
        List<FounderRankingResponse.RankingItem> items =
                new ArrayList<>();

        int size = Math.min(limit, candidates.size());

        for (int i = 0; i < size; i++) {
            FounderRankingResponse.RankingItem item = candidates.get(i);

            items.add(new FounderRankingResponse.RankingItem(
                    i + 1,
                    item.dongId(),
                    item.name(),
                    item.requestCount(),
                    item.sameCategoryStoreCount(),
                    item.nearestMartKm(),
                    item.score()
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
}
