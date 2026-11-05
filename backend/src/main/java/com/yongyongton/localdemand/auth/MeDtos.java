package com.yongyongton.localdemand.auth;

import jakarta.validation.constraints.NotNull;

/** GET /me, PUT /me 요청·응답 (명세 5-1). */
public final class MeDtos {

    private MeDtos() {
    }

    /** PUT /me 요청. 예: { "role": "RESIDENT", "dongId": 101 }. dongId를 빼면 지금 동네를 그대로 둬요. */
    public record UpdateMeRequest(@NotNull Role role, Long dongId) {
    }

    /** GET /me, PUT /me 응답. 동네·가게가 없으면 null. */
    public record MeResponse(Long id, String nickname, Role role, DongView dong, ShopView shop) {
    }

    /** 내 동네: { "id": 101, "name": "구성동", "gu": "기흥구" } */
    public record DongView(Long id, String name, String gu) {
    }

    /** 사장님 가게 (PUT /me/shop 응답의 shop 과 같은 모양). */
    public record ShopView(String shopName, CategoryView category, ShopDongView dong, Double lat, Double lon) {
    }

    public record CategoryView(Long id, String name) {
    }

    /** 가게 동네: { "id": 104, "name": "신갈동" } (명세 예시대로 gu 없음) */
    public record ShopDongView(Long id, String name) {
    }
}
