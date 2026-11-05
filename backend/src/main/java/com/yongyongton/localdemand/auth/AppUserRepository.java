package com.yongyongton.localdemand.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 시연 계정 찾기. 같은 닉네임·역할 계정이 여러 개면 제일 먼저 만든 것. */
    Optional<AppUser> findFirstByNicknameAndRoleOrderByIdAsc(String nickname, Role role);
}
