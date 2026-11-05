package com.yongyongton.localdemand.auth;

import com.yongyongton.localdemand.auth.AuthDtos.AuthResponse;
import com.yongyongton.localdemand.auth.AuthDtos.DemoLoginRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 (명세 5-1). /auth/** 는 SecurityConfig에서 로그인 없이 열려 있어요. */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 처음 들어오면 프론트가 한 번 불러요. 요청 본문 없음. */
    @PostMapping("/auth/guest")
    public AuthResponse guest() {
        return authService.createGuest();
    }

    /** 심사·발표용. 운영에서는 DEMO_LOGIN_ENABLED=false로 꺼요. */
    @PostMapping("/auth/demo")
    public AuthResponse demo(@Valid @RequestBody DemoLoginRequest request) {
        return authService.demoLogin(request.role());
    }
}
