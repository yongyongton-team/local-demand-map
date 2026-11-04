package com.yongyongton.localdemand.request;

import com.yongyongton.localdemand.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 주민 요청 (V1 shop_request). 같은 사람·같은 동·같은 업종은 한 번만(uk_request_user_dong_category).
 * dong·category 는 A 담당 테이블이라 id(Long)로만 들고 있어요.
 * 취소(DELETE /requests/{id})는 행을 지워요.
 */
@Entity
@Table(name = "shop_request", uniqueConstraints = @UniqueConstraint(
        name = "uk_request_user_dong_category", columnNames = {"user_id", "dong_id", "category_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "dong_id", nullable = false)
    private Long dongId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /** 주민이 쓴 문장(200자 이내). 업종만 골랐으면 NULL. 로그에 남기지 않아요. */
    @Column(name = "text", length = 200)
    private String text;

    @Convert(converter = RequestConditionListConverter.class)
    @Column(name = "conditions", nullable = false, length = 200)
    private List<RequestCondition> conditions = List.of();

    @Convert(converter = TimeSlotListConverter.class)
    @Column(name = "time_slots", nullable = false, length = 100)
    private List<TimeSlot> timeSlots = List.of();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "frequency", length = 20)
    private Frequency frequency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ShopRequest(AppUser user, Long dongId, Long categoryId, String text,
                       List<RequestCondition> conditions, List<TimeSlot> timeSlots, Frequency frequency) {
        this.user = user;
        this.dongId = dongId;
        this.categoryId = categoryId;
        this.text = text;
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
        this.timeSlots = timeSlots == null ? List.of() : List.copyOf(timeSlots);
        this.frequency = frequency;
    }
}
