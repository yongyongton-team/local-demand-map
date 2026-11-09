package com.yongyongton.localdemand.dong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "dong")
public class Dong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adm_code", nullable = false, length = 10)
    private String admCode;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "gu", nullable = false, length = 10)
    private String gu;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lon", nullable = false)
    private double centerLon;

    @Lob
    @Column(name = "boundary", columnDefinition = "LONGTEXT")
    private String boundary;

    /** 주민등록 인구(2026-09, V8). 모르면 null. */
    @Column(name = "population")
    private Integer population;

    protected Dong() {
    }

    public Long getId() {
        return id;
    }

    public String getAdmCode() {
        return admCode;
    }

    public String getName() {
        return name;
    }

    public String getGu() {
        return gu;
    }

    public double getCenterLat() {
        return centerLat;
    }

    public double getCenterLon() {
        return centerLon;
    }

    public String getBoundary() {
        return boundary;
    }

    public Integer getPopulation() {
        return population;
    }
}
