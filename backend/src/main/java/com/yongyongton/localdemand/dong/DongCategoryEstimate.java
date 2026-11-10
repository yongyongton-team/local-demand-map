package com.yongyongton.localdemand.dong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 동네·업종마다 공공데이터 기대치 모델 결과 (V8, 39개 동 × 38개 업종).
 * "인구 구성으로 보면 보통 몇 곳인데 동 안에는 몇 곳"을 창업자 리포트의 근거로 써요. 읽기 전용이에요.
 */
@Entity
@Table(name = "dong_category_estimate")
public class DongCategoryEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dong_id", nullable = false)
    private Long dongId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "model_gain", nullable = false)
    private double modelGain;

    @Column(name = "expected_count", nullable = false)
    private double expectedCount;

    @Column(name = "dong_store_count", nullable = false)
    private int dongStoreCount;

    @Column(name = "prob_fewer", nullable = false)
    private double probFewer;

    @Column(name = "radius_1km_count")
    private Integer radius1kmCount;

    @Column(name = "radius_1_5km_count")
    private Integer radius15kmCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "verdict", nullable = false, length = 20)
    private EstimateVerdict verdict;

    protected DongCategoryEstimate() {
    }

    public Long getDongId() {
        return dongId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public double getModelGain() {
        return modelGain;
    }

    public double getExpectedCount() {
        return expectedCount;
    }

    public int getDongStoreCount() {
        return dongStoreCount;
    }

    public double getProbFewer() {
        return probFewer;
    }

    public Integer getRadius1kmCount() {
        return radius1kmCount;
    }

    public Integer getRadius15kmCount() {
        return radius15kmCount;
    }

    public EstimateVerdict getVerdict() {
        return verdict;
    }
}
