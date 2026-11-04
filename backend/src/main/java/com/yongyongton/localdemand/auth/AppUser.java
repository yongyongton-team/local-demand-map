package com.yongyongton.localdemand.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 계정 (V1 app_user). 손님 계정은 역할·동네 없이 만들고, 화면 0에서 PUT /me 로 정해요.
 * dong 은 A 담당 테이블이라 연관관계 대신 id(Long)로만 들고 있어요.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카카오 회원번호. MVP(손님·시연 계정)에서는 항상 NULL. */
    @Column(name = "kakao_id")
    private Long kakaoId;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    /** 역할을 고르기 전에는 NULL. Hibernate가 MySQL ENUM 타입으로 착각하지 않게 VARCHAR로 못 박아요. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", length = 20)
    private Role role;

    /** 주민 위치는 동까지만 (dong.id). */
    @Column(name = "dong_id")
    private Long dongId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AppUser(String nickname) {
        this.nickname = nickname;
    }

    /** PUT /me · 역할·동네 정하기(역할 바꾸기 포함). */
    public void changeProfile(Role role, Long dongId) {
        this.role = role;
        this.dongId = dongId;
    }
}
