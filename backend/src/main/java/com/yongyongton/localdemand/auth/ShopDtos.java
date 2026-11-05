package com.yongyongton.localdemand.auth;

import com.yongyongton.localdemand.auth.MeDtos.ShopView;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /me/shop 요청·응답 (명세 5-1). */
public final class ShopDtos {

    private ShopDtos() {
    }

    /** 예: { "shopName": "싱싱과일", "categoryId": 7, "lat": 37.27, "lon": 127.11 } */
    public record UpdateShopRequest(
            @NotBlank @Size(max = 50) String shopName,
            @NotNull Long categoryId,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon) {
    }

    /** 예: { "shop": { "shopName": ..., "category": {...}, "dong": {...}, "lat": ..., "lon": ... } } */
    public record ShopResponse(ShopView shop) {
    }
}
