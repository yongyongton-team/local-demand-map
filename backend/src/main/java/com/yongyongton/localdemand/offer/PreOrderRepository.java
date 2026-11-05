package com.yongyongton.localdemand.offer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PreOrderRepository extends JpaRepository<PreOrder, Long> {

    Optional<PreOrder> findByOfferIdAndUserId(Long offerId, Long userId);

    List<PreOrder> findByOfferIdAndStatus(Long offerId, OrderStatus status);

    /** 주문 수(orderCount) = 취소 안 한 선주문 수량 합계. */
    @Query("SELECT COALESCE(SUM(p.quantity), 0L) FROM PreOrder p WHERE p.offer.id = :offerId AND p.status <> :cancelled")
    Long sumQuantity(@Param("offerId") Long offerId, @Param("cancelled") OrderStatus cancelled);
}
