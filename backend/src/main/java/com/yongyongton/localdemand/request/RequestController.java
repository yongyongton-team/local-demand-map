package com.yongyongton.localdemand.request;

import com.yongyongton.localdemand.request.RequestDtos.CreateRequest;
import com.yongyongton.localdemand.request.RequestDtos.CreateResponse;
import com.yongyongton.localdemand.request.RequestDtos.MyRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주민 요청 (명세 5-3, 화면 2·3). 로그인 필요. */
@RestController
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /** 요청 보내기 (화면 2 → 3). 201. */
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        return requestService.create(jwt, request);
    }

    /** 내 요청 (화면 3). */
    @GetMapping("/me/requests")
    public List<MyRequest> myRequests(@AuthenticationPrincipal Jwt jwt) {
        return requestService.myRequests(jwt);
    }

    /** 요청 취소. 204. */
    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        requestService.delete(jwt, id);
    }
}
