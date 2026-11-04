package com.yongyongton.localdemand.auth;

import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 토큰으로 지금 로그인한 계정을 찾아요. 다른 패키지에서도 이렇게 써요.
 * <pre>
 * &#64;GetMapping("/owner/demand")
 * public ... demand(&#64;AuthenticationPrincipal Jwt jwt) {
 *     AppUser owner = currentUser.require(jwt, Role.OWNER); // 사장님이 아니면 403
 * }
 * </pre>
 */
@Component
public class CurrentUser {

    private final AppUserRepository userRepository;

    public CurrentUser(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 토큰의 계정. 계정이 지워졌으면(예: 로컬 DB 초기화) 401 → 프론트가 /auth/guest 로 새 토큰을 받아요. */
    public AppUser get(Jwt jwt) {
        return userRepository.findById(userId(jwt))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    /** 역할 전용 API. 역할이 다르거나 아직 안 골랐으면 403. */
    public AppUser require(Jwt jwt, Role role) {
        AppUser user = get(jwt);
        if (user.getRole() != role) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 계정 id만 필요할 때(DB를 안 읽어요). */
    public static Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }
}
