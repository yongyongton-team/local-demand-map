package com.yongyongton.localdemand.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** POST /auth/guest, POST /auth/demo 요청·응답 (명세 5-1). */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** POST /auth/demo 요청. 예: { "role": "RESIDENT" } */
    public record DemoLoginRequest(@NotNull Role role) {
    }

    /** 두 API 응답 모양은 같아요. */
    public record AuthResponse(
            String accessToken,
            long expiresIn,
            @JsonProperty("isNew") boolean isNew,
            UserSummary user) {
    }

    public record UserSummary(Long id, String nickname, Role role, Long dongId) {

        static UserSummary from(AppUser user) {
            return new UserSummary(user.getId(), user.getNickname(), user.getRole(), user.getDongId());
        }
    }
}
