# backend

API 서버예요. 명세는 팀 노션의 "API 명세 초안 v0"을 따라요.

- 주소는 모두 `/api/v1` 아래
- 같은 동네·같은 업종 중복 요청은 DB 고유 제약 `(user_id, dong_id, category_id)`으로 막아요
- 시연용 로그인 `POST /auth/demo`는 `DEMO_LOGIN_ENABLED=false`면 꺼져요

## 실행 방법

기술 스택이 정해지면 적어요.
