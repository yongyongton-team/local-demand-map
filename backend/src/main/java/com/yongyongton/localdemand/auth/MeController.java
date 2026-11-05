package com.yongyongton.localdemand.auth;

import com.yongyongton.localdemand.auth.MeDtos.MeResponse;
import com.yongyongton.localdemand.auth.MeDtos.UpdateMeRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 (명세 5-1, 화면 0). 로그인 필요. */
@RestController
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return meService.get(jwt);
    }

    /** 역할·동네 정하기. 응답은 GET /me 와 같아요. */
    @PutMapping("/me")
    public MeResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateMeRequest request) {
        return meService.update(jwt, request);
    }
}
