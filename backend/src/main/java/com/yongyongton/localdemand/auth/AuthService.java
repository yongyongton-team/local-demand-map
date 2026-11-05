package com.yongyongton.localdemand.auth;

import com.yongyongton.localdemand.auth.AuthDtos.AuthResponse;
import com.yongyongton.localdemand.auth.AuthDtos.UserSummary;
import com.yongyongton.localdemand.common.error.ApiException;
import com.yongyongton.localdemand.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String GUEST_PREFIX = "손님";

    private final AppUserRepository userRepository;
    private final TokenService tokenService;
    private final boolean demoLoginEnabled;

    public AuthService(AppUserRepository userRepository, TokenService tokenService,
                       @Value("${app.demo-login.enabled:false}") boolean demoLoginEnabled) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.demoLoginEnabled = demoLoginEnabled;
    }

    /** POST /auth/guest · 손님 계정을 새로 만들고 입장권을 줘요. 닉네임은 "손님" + 계정 번호. */
    @Transactional
    public AuthResponse createGuest() {
        AppUser user = userRepository.save(new AppUser(GUEST_PREFIX));
        user.changeNickname(GUEST_PREFIX + user.getId());
        return respond(user, true);
    }

    /**
     * POST /auth/demo · 역할별 시연 계정으로 로그인. DEMO_LOGIN_ENABLED=false면 없는 주소처럼 404.
     * 시연 계정이 없으면 이때 만들어요(동네·예시 요청·가게는 시드에서 채워요).
     */
    @Transactional
    public AuthResponse demoLogin(Role role) {
        if (!demoLoginEnabled) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        String nickname = demoNickname(role);
        Optional<AppUser> existing = userRepository.findFirstByNicknameAndRoleOrderByIdAsc(nickname, role);
        if (existing.isPresent()) {
            return respond(existing.get(), false);
        }
        AppUser user = new AppUser(nickname);
        user.changeProfile(role, null);
        return respond(userRepository.save(user), true);
    }

    static String demoNickname(Role role) {
        return switch (role) {
            case RESIDENT -> "시연 주민";
            case OWNER -> "시연 사장님";
            case FOUNDER -> "시연 창업자";
        };
    }

    private AuthResponse respond(AppUser user, boolean isNew) {
        return new AuthResponse(tokenService.issue(user.getId()), tokenService.ttlSeconds(), isNew,
                UserSummary.from(user));
    }
}
