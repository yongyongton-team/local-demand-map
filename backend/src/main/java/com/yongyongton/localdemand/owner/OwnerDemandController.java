package com.yongyongton.localdemand.owner;

import com.yongyongton.localdemand.owner.OwnerDtos.DemandResponse;
import com.yongyongton.localdemand.owner.OwnerDtos.DetailResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 사장님 화면 (명세 5-5, 화면 4·5). 사장님만. */
@RestController
public class OwnerDemandController {

    private final OwnerDemandService ownerDemandService;

    public OwnerDemandController(OwnerDemandService ownerDemandService) {
        this.ownerDemandService = ownerDemandService;
    }

    /** 요청 모아 보기 (화면 4). 칩 "3km 안 / 5km 안"은 ?radiusKm=3|5, "기흥구 전체"는 ?gu=기흥구. */
    @GetMapping("/owner/demand")
    public DemandResponse demand(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) Double radiusKm,
                                 @RequestParam(required = false) String gu) {
        return ownerDemandService.demand(jwt, radiusKm, gu);
    }

    /** 동네 요청 자세히 (화면 5). ?categoryId= 를 빼면 내 가게 업종. */
    @GetMapping("/owner/demand/{dongId}")
    public DetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long dongId,
                                 @RequestParam(required = false) Long categoryId) {
        return ownerDemandService.detail(jwt, dongId, categoryId);
    }
}
