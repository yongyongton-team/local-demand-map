package com.yongyongton.localdemand.dong;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DongCategoryEstimateRepository extends JpaRepository<DongCategoryEstimate, Long> {

    Optional<DongCategoryEstimate> findByDongIdAndCategoryId(Long dongId, Long categoryId);
}
