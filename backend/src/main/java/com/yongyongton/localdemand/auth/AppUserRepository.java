package com.yongyongton.localdemand.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * 닉네임으로 찾기(시연 계정·예시 주민). 같은 닉네임이 여러 개면 제일 먼저 만든 것.
     * 역할은 PUT /me 로 바뀔 수 있어서 역할로는 찾지 않아요.
     */
    Optional<AppUser> findFirstByNicknameOrderByIdAsc(String nickname);
}
