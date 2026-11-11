package com.yongyongton.localdemand.dong;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DongCategoryEstimateRepository extends JpaRepository<DongCategoryEstimate, Long> {

    Optional<DongCategoryEstimate> findByDongIdAndCategoryId(Long dongId, Long categoryId);

    /** 한 업종의 동별 모델 결과(창업자 순위용). 39개 동이 다 있어요. */
    List<DongCategoryEstimate> findByCategoryId(Long categoryId);
}
