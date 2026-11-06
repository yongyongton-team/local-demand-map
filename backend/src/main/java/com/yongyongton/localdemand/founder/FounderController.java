package com.yongyongton.localdemand.founder;

import com.yongyongton.localdemand.auth.CurrentUser;
import com.yongyongton.localdemand.auth.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FounderController {

    private final FounderService founderService;
    private final CurrentUser currentUser;

    public FounderController(
            FounderService founderService,
            CurrentUser currentUser
    ) {
        this.founderService = founderService;
        this.currentUser = currentUser;
    }

    @GetMapping("/founder/rankings")
    public FounderRankingResponse getRankings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long categoryId,
            @RequestParam int limit
    ) {
        currentUser.require(jwt, Role.FOUNDER);
        return founderService.getRankings(categoryId, limit);
    }
}
