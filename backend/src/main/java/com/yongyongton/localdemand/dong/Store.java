package com.yongyongton.localdemand.dong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, length = 30)
    private String sourceId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lon", nullable = false)
    private double lon;

    @Column(name = "dong_id")
    private Long dongId;

    protected Store() {
    }

    public Long getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public Long getDongId() {
        return dongId;
    }
}
