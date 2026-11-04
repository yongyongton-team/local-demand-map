package com.yongyongton.localdemand.dong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gap")
public class Gap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dong_id", nullable = false)
    private Long dongId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "walk_shop_count", nullable = false)
    private int walkShopCount;

    @Column(name = "nearest_mart_id")
    private Long nearestMartId;

    @Column(name = "nearest_mart_km")
    private Double nearestMartKm;

    protected Gap() {
    }

    public Long getId() {
        return id;
    }

    public Long getDongId() {
        return dongId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getStatus() {
        return status;
    }

    public int getWalkShopCount() {
        return walkShopCount;
    }

    public Long getNearestMartId() {
        return nearestMartId;
    }

    public Double getNearestMartKm() {
        return nearestMartKm;
    }
}
