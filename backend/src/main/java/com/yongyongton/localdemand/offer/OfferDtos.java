package com.yongyongton.localdemand.offer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 선주문 수령 API 요청·응답 (명세 5-4, 5-5). 시각은 ISO 8601 한국 시간(+09:00). */
public final class OfferDtos {

    private OfferDtos() {
    }

    /** POST /offers 요청. 조건: 지금 < deadlineAt < pickupAt, minOrders 1~100. */
    public record CreateOfferRequest(
            @NotNull Long dongId,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 50) String title,
            @Size(max = 500) String description,
            @NotNull @PositiveOrZero Integer price,
            @NotNull OffsetDateTime pickupAt,
            @NotBlank @Size(max = 100) String pickupPlace,
            @NotNull @Min(1) @Max(100) Integer minOrders,
            @NotNull OffsetDateTime deadlineAt) {
    }

    /** POST /offers 201: { "offer": {...}, "notifiedResidentCount": 30 } */
    public record CreateOfferResponse(OfferSummary offer, long notifiedResidentCount) {
    }

    public record OfferSummary(Long id, OfferStatus status, long orderCount, int minOrders) {
    }

    /** GET /owner/offers 한 줄. cancel·done 응답도 같은 모양. */
    public record OwnerOfferItem(
            Long id, IdName dong, String title, long orderCount, int minOrders,
            OfferStatus status, OffsetDateTime deadlineAt) {
    }

    /** POST /offers/{id}/orders 요청. 수량 1~10, 빼면 1. */
    public record OrderRequest(@Min(1) @Max(10) Integer quantity) {
    }

    /** POST /offers/{id}/orders 201: { "order": {...}, "orderCount": 13, "minOrders": 20 } */
    public record OrderResponse(OrderView order, long orderCount, int minOrders) {
    }

    /** 선주문 한 건. GET /me/requests 의 myOrder 도 같은 모양. */
    public record OrderView(Long id, Long offerId, int quantity, OrderStatus status) {
    }

    /** GET /me/requests 의 offers 한 칸 (명세 5-3). myOrder 는 주문 안 했거나 취소했으면 null. */
    public record ResidentOfferView(
            Long id, String shopName, String title, int price, OffsetDateTime pickupAt, String pickupPlace,
            int minOrders, long orderCount, OffsetDateTime deadlineAt, OfferStatus status, OrderView myOrder) {
    }

    public record IdName(Long id, String name) {
    }
}
