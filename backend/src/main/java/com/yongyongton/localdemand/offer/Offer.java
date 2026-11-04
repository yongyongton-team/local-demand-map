package com.yongyongton.localdemand.offer;

import com.yongyongton.localdemand.auth.AppUser;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import jakarta.persistence.Column;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 선주문 수령 제안 (V2 offer, 결정 4를 채택할 때만).
 * dong·category 는 A 담당 테이블이라 id(Long)로만 들고 있어요.
 * 마감 뒤 CONFIRMED/CLOSED 판정은 명세대로 서비스(또는 조회할 때)에서 해요.
 */
@Entity
@Table(name = "offer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 제안한 사장님 (app_user.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "dong_id", nullable = false)
    private Long dongId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    /** 원. 결제는 MVP에서 안 하고 현장 결제. */
    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "pickup_at", nullable = false)
    private LocalDateTime pickupAt;

    @Column(name = "pickup_place", nullable = false, length = 100)
    private String pickupPlace;

    /** 1~100. */
    @Column(name = "min_orders", nullable = false)
    private Integer minOrders;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OfferStatus status = OfferStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Offer(AppUser owner, Long dongId, Long categoryId, String title, String description, Integer price,
                 LocalDateTime pickupAt, String pickupPlace, Integer minOrders, LocalDateTime deadlineAt) {
        this.owner = owner;
        this.dongId = dongId;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.pickupAt = pickupAt;
        this.pickupPlace = pickupPlace;
        this.minOrders = minOrders;
        this.deadlineAt = deadlineAt;
        this.status = OfferStatus.OPEN;
    }

    /** POST /offers/{id}/cancel · OPEN·CONFIRMED일 때만 (아니면 409 OFFER_CLOSED). */
    public void cancel() {
        if (status != OfferStatus.OPEN && status != OfferStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.OFFER_CLOSED);
        }
        this.status = OfferStatus.CANCELLED;
    }

    /** POST /offers/{id}/done · 수령 끝. 허용 상태는 명세에 없어 서비스에서 정해요. */
    public void markDone() {
        this.status = OfferStatus.DONE;
    }

    /** 마감 시각이 지난 뒤 주문 수로 CONFIRMED / CLOSED 를 정할 때. */
    public void changeStatus(OfferStatus status) {
        this.status = status;
    }
}
