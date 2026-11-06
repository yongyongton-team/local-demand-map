package com.yongyongton.localdemand.dong;

import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import com.yongyongton.localdemand.request.ShopRequestRepository;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DongService {

    private final DongRepository dongRepository;
    private final GapRepository gapRepository;
    private final ShopRequestRepository shopRequestRepository;
    private final EntityManager em;

    public DongService(
            DongRepository dongRepository,
            GapRepository gapRepository,
            ShopRequestRepository shopRequestRepository,
            EntityManager em
    ) {
        this.dongRepository = dongRepository;
        this.gapRepository = gapRepository;
        this.shopRequestRepository = shopRequestRepository;
        this.em = em;
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
}
