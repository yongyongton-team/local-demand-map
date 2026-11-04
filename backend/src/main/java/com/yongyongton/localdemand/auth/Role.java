package com.yongyongton.localdemand.auth;

/** 계정 역할 (명세 3. 값 목록). app_user.role 에 이름 그대로 저장해요. */
public enum Role {
    RESIDENT, // 주민
    OWNER,    // 사장님
    FOUNDER   // 예비 창업자
}
