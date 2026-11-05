package com.yongyongton.localdemand.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerShopRepository extends JpaRepository<OwnerShop, Long> {

    /** 사장님 한 명에 가게 하나(uk_owner_shop_user). */
    Optional<OwnerShop> findByUserId(Long userId);
}
