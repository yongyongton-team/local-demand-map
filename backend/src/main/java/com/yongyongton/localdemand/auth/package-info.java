/**
 * 로그인·내 정보 (명세 5-1).
 * POST /auth/guest(손님 계정), POST /auth/demo(DEMO_LOGIN_ENABLED=false면 끔), GET /me, PUT /me, PUT /me/shop.
 * 카카오 로그인은 MVP에서 뺐어요. 손님 토큰은 길게(30일) 줘요.
 * 토큰(JWT) 확인 필터도 여기에 만들어 SecurityConfig 에 붙여요.
 */
package com.yongyongton.localdemand.auth;
