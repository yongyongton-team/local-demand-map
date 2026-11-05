package com.yongyongton.localdemand.offer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    /** 사장님 내 제안, 최근 것이 위. */
    List<Offer> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    /** 주민 화면 3: 그 동·업종 제안 중 취소된 것만 빼고. */
    List<Offer> findByDongIdAndCategoryIdAndStatusNotOrderByCreatedAtDescIdDesc(
            Long dongId, Long categoryId, OfferStatus status);
}
