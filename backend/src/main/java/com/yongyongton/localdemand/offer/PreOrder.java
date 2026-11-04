package com.yongyongton.localdemand.offer;

import com.yongyongton.localdemand.auth.AppUser;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 선주문 (V2 pre_order, 결정 4를 채택할 때만). 한 사람은 한 제안에 한 번만(uk_pre_order_offer_user). */
@Entity
@Table(name = "pre_order", uniqueConstraints = @UniqueConstraint(
        name = "uk_pre_order_offer_user", columnNames = {"offer_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.RESERVED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PreOrder(Offer offer, AppUser user, Integer quantity) {
        this.offer = offer;
        this.user = user;
        this.quantity = quantity == null ? 1 : quantity;
        this.status = OrderStatus.RESERVED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void markPickedUp() {
        this.status = OrderStatus.PICKED_UP;
    }
}
