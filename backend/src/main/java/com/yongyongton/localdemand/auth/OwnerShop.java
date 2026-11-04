package com.yongyongton.localdemand.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사장님 가게 (V1 owner_shop). 사장님 한 명에 가게 하나(uk_owner_shop_user).
 * category·dong 은 A 담당 테이블이라 id(Long)로만 들고 있어요.
 */
@Entity
@Table(name = "owner_shop")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnerShop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "shop_name", nullable = false, length = 50)
    private String shopName;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lon", nullable = false)
    private Double lon;

    /** 좌표로 서버가 찾은 동. 용인시 밖이면 NULL. */
    @Column(name = "dong_id")
    private Long dongId;

    public OwnerShop(AppUser user, String shopName, Long categoryId, Double lat, Double lon, Long dongId) {
        this.user = user;
        this.shopName = shopName;
        this.categoryId = categoryId;
        this.lat = lat;
        this.lon = lon;
        this.dongId = dongId;
    }

    /** PUT /me/shop 을 다시 부르면 덮어써요. */
    public void update(String shopName, Long categoryId, Double lat, Double lon, Long dongId) {
        this.shopName = shopName;
        this.categoryId = categoryId;
        this.lat = lat;
        this.lon = lon;
        this.dongId = dongId;
    }
}
