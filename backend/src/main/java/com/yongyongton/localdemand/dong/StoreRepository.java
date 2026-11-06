package com.yongyongton.localdemand.dong;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByDongId(Long dongId);

    List<Store> findByDongIdAndCategoryId(Long dongId, Long categoryId);
}
