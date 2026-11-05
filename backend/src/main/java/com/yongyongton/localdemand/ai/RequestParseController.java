package com.yongyongton.localdemand.ai;

import com.yongyongton.localdemand.ai.ParseDtos.ParseRequest;
import com.yongyongton.localdemand.ai.ParseDtos.ParseResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 요청 문장 AI 정리 (명세 5-3, 화면 2). 주민만. 프론트는 입력이 0.8초 멈추면 한 번만 불러요. */
@RestController
public class RequestParseController {

    private final RequestParseService requestParseService;

    public RequestParseController(RequestParseService requestParseService) {
        this.requestParseService = requestParseService;
    }

    @PostMapping("/requests/parse")
    public ParseResponse parse(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ParseRequest request) {
        return requestParseService.parse(jwt, request);
    }
}
