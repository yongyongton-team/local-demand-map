package com.yongyongton.localdemand.dong;

import com.yongyongton.localdemand.auth.CurrentUser;
import com.yongyongton.localdemand.auth.Role;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class DongController {

    private final DongService dongService;
    private final CurrentUser currentUser;

    public DongController(DongService dongService, CurrentUser currentUser) {
        this.dongService = dongService;
        this.currentUser = currentUser;
    }

    @GetMapping("/dongs")
    public List<DongResponse> getDongs() {
        return dongService.getDongs().stream()
                .map(dong -> new DongResponse(
                        dong.getId(),
                        dong.getName(),
                        dong.getGu(),
                        new DongMapResponse.Center(dong.getCenterLat(), dong.getCenterLon())
                ))
                .toList();
    }

    @GetMapping("/dongs/{dongId}/gaps")
    public List<GapResponse> getGaps(
            @PathVariable Long dongId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = currentUser.require(jwt, Role.RESIDENT).getId();
        return dongService.getGapResponses(dongId, userId);
    }

    /** center 는 /dongs/{dongId}/map 의 dong.center 와 같은 값·모양이에요(창업자 지도처럼 동 위치만 필요할 때). */
    public record DongResponse(Long id, String name, String gu, DongMapResponse.Center center) {
    }

    @GetMapping("/dongs/{dongId}/map")
    public DongMapResponse getDongMap(
            @PathVariable Long dongId,
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        currentUser.require(jwt, Role.RESIDENT);
        return dongService.getDongMap(dongId, categoryId);
    }
}
