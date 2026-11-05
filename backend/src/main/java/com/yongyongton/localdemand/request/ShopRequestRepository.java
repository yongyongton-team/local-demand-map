package com.yongyongton.localdemand.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRequestRepository extends JpaRepository<ShopRequest, Long> {

    boolean existsByUserIdAndDongIdAndCategoryId(Long userId, Long dongId, Long categoryId);

    /** 그 동·업종의 요청 수 (화면 1·3의 "N명"). */
    long countByDongIdAndCategoryId(Long dongId, Long categoryId);

    /** 내 요청, 최근 것이 맨 위. */
    List<ShopRequest> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
