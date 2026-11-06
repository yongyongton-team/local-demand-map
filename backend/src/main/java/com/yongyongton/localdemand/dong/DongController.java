package com.yongyongton.localdemand.dong;

import com.yongyongton.localdemand.auth.CurrentUser;
import com.yongyongton.localdemand.auth.Role;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
                        dong.getGu()
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

    public record DongResponse(Long id, String name, String gu) {
    }
}
