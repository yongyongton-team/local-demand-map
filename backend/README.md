# backend

API 서버예요. 명세는 팀 노션의 "API 명세 초안 v0"을 따라요.

- 주소는 모두 `/api/v1` 아래
- 같은 동네·같은 업종 중복 요청은 DB 고유 제약 `(user_id, dong_id, category_id)`으로 막아요
- 시연용 로그인 `POST /auth/demo`는 `DEMO_LOGIN_ENABLED=false`면 꺼져요

## 기술 스택

Java 21, Spring Boot 4.1, Gradle, MySQL 8.4, Flyway, Spring Security, springdoc(Swagger)

## 준비물

- JDK 21 (`java -version`으로 확인. 다른 버전이면 IntelliJ에서 프로젝트 SDK를 21로)
- Docker Desktop (로컬 MySQL을 띄워요)

## 실행 방법

```bash
cd backend
./gradlew bootRun
```

- Docker Desktop을 켜 두면 `compose.yaml`의 MySQL(포트 3307)이 자동으로 떠요. 처음에는 이미지를 받느라 조금 걸려요.
- 서버가 뜰 때 Flyway가 `src/main/resources/db/migration`의 SQL로 테이블을 만들어요.
- 확인: http://localhost:8080/api/v1/health → `{"status":"UP"}`
- Swagger: http://localhost:8080/swagger-ui.html
- IntelliJ로 열 때는 `backend/` 폴더를 열어요(레포 루트를 열면 `compose.yaml`을 못 찾아요).

환경변수는 레포 루트의 `.env`에서 읽어요(`cp .env.example .env`). 로컬은 기본값으로도 돌아가요.

## 테스트

```bash
./gradlew test
```

- `ApiBasicsTest`: DB 없이 공통 규칙(주소, 로그인 확인, 오류 모양, CORS)을 봐요.
- `ApplicationStartTest`: 실제 MySQL로 마이그레이션과 서버 시작을 봐요. Docker가 꺼져 있으면 건너뛰고, PR을 올리면 GitHub Actions에서 항상 돌아요.
- AI 테스트는 실제 AI API를 부르지 않아요. 가짜 AI(Mockito)나 테스트 안에 띄운 가짜 HTTP 서버를 써요.

## AI 쓰기

`AI_PROVIDER`로 골라요. 기본값은 `fake`라서 키 없이도 서버가 떠요.

| AI_PROVIDER | 요청 문장 정리 | 요약 문장(화면 5·7) |
|---|---|---|
| `fake` (기본) | AI 없이 업종 이름·조건 낱말이 문장에 있는지만 봐요. 응답 `by: "KEYWORD"` | 항상 서버 기본 문장(`by: "TEMPLATE"`) |
| `openai` | OpenAI Chat Completions로 모델을 불러요. 응답 `by: "AI"` | AI 문장. 검사에서 떨어지면 기본 문장 |

- OpenAI로 켜기: 레포 루트 `.env`에 `AI_PROVIDER=openai`와 `AI_API_KEY=키`만 넣으면 돼요. 모델은 기본값 `gpt-6-luna`(GPT-6 중 가장 싸고 빠름), `reasoning_effort`는 `none`(생각 없이 바로 답함)이에요. 업종 고르기·짧은 요약에는 이걸로 충분하고, 주민이 입력을 멈출 때마다 부르니 빨라야 해요.
- 모델을 바꾸면(`AI_MODEL`) 그 모델이 `reasoning_effort: none`을 받는지 OpenAI 모델 페이지에서 확인해요. 안 받으면 `AI_REASONING_EFFORT=low`.
- `openai`인데 키가 비면 서버가 켜지지 않아요(배포 실수를 바로 알려고). 키는 `.env`(커밋 안 함)나 서버 환경변수에만 두고 단톡방·캡처에 올리지 않아요.
- 요약 문장 검사(`SummaryValidator`): 문장 속 숫자가 서버가 넣은 숫자에 없거나, "확실히·반드시·무조건·보장" 같은 말이 있으면 버리고 기본 문장을 써요. 통과한 문장은 `ai_summary`에 저장해 같은 숫자면 AI를 다시 부르지 않아요. 지시문을 바꾸면 `SummaryService.PROMPT_VERSION`을 올려요.
- 지시문(프롬프트)은 `LlmAiWriter`에 있어요. 주민 문장은 `<주민문장>` 구분자로 감싸고 안의 지시를 따르지 말라고 적어요. JSON이 깨지면 한 번만 다시 불러요.

## 폴더(패키지)

`src/main/java/com/yongyongton/localdemand/` 아래에 명세 화면별로 나눴어요. 각 폴더의 `package-info.java`에 맡을 API가 적혀 있어요.

| 패키지 | 맡는 것 |
|---|---|
| `auth` | 손님·시연용 로그인, 내 정보, 사장님 가게 등록, 토큰 확인 필터 |
| `dong` | 동·업종 목록, 동네 지도, 빈 업종 |
| `request` | 주민 요청(보내기, 내 요청, 취소) |
| `offer` | 선주문 수령(채택할지 정하는 중) |
| `owner` | 사장님 요청 모아 보기·자세히 |
| `founder` | 창업자 입지 순위·동네 리포트 |
| `ai` | AI 호출(요청 문장 정리 `POST /requests/parse`, 요약 문장과 숫자 검사) |
| `common` | 오류 처리, 보안, CORS, Swagger, 서버 상태 |

## 같이 지킬 것

- **주소**: 컨트롤러에는 명세처럼 `@GetMapping("/dongs")`만 써요. 앞에 `/api/v1`이 자동으로 붙어요(`WebConfig`).
- **오류**: `throw new ApiException(ErrorCode.ALREADY_REQUESTED)`처럼 던지면 `{ "code", "message" }`로 바뀌어요. 새 코드가 필요하면 `ErrorCode`와 노션 명세를 같이 고쳐요.
- **로그인 없이 여는 API**: `SecurityConfig`의 `PUBLIC_PATHS`에 더해요. 지금은 `/health`, `/auth/**`, Swagger만 열려 있고 나머지는 토큰이 없거나 틀리면 401이에요. `/auth/**`와 `/health`에서는 토큰을 아예 안 봐요(`JwtConfig`).
- **내 계정 꺼내기**: 컨트롤러에서 `@AuthenticationPrincipal Jwt jwt`로 받고, 서비스에서 `CurrentUser`를 써요. `currentUser.get(jwt)`는 계정(`AppUser`), `currentUser.require(jwt, Role.OWNER)`는 역할이 다르면 403, `CurrentUser.userId(jwt)`는 DB를 안 읽고 id만 줘요.
- **Swagger에서 로그인한 채로 불러 보기**: `POST /api/v1/auth/guest`(또는 `/auth/demo`)로 받은 `accessToken`을 오른쪽 위 Authorize에 넣어요.
- **테이블 바꾸기**: main에 합친 마이그레이션 파일은 고치지 않아요. `V3__add_xxx.sql`처럼 다음 번호로 새 파일을 더해요. 브랜치끼리 번호가 겹치면 나중에 합치는 쪽이 번호를 바꿔요.
- **엔티티**: `ddl-auto: validate`라서 엔티티와 테이블이 다르면 서버가 안 떠요. 오류 메시지에 어느 칼럼이 다른지 나와요.
- **응답**: 엔티티를 그대로 내보내지 말고 DTO(`record`)로 만들어요. `open-in-view`를 꺼 둬서 서비스 밖에서는 지연 로딩이 안 돼요.
- **중복 요청**: 서비스에서 먼저 확인하고, 동시에 두 번 들어와 고유 제약에 걸리면(`DataIntegrityViolationException`) `ALREADY_REQUESTED`로 바꿔요.
- **비밀 값**: 카카오·AI 키는 `.env`(커밋 안 함)나 서버 환경변수에만 둬요. 주민 문장·AI 응답 본문은 로그에 남기지 않아요.

## Spring Boot 4에서 헷갈리는 것

인터넷 예제는 대부분 Boot 3 기준이라 그대로 따라 하면 막힐 수 있어요.

- 스타터 이름이 바뀌었어요: `spring-boot-starter-web` → `spring-boot-starter-webmvc`, 테스트는 `spring-boot-starter-webmvc-test`. Flyway도 `spring-boot-starter-flyway`를 써요.
- Jackson이 3.x예요. `com.fasterxml.jackson.databind.ObjectMapper`가 아니라 `tools.jackson.databind.ObjectMapper`예요(어노테이션 `com.fasterxml.jackson.annotation.*`은 그대로).
- 테스트 어노테이션 위치: `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`, `@AutoConfigureMockMvc`도 같은 곳이에요.
- Swagger(springdoc)는 3.x를 써요. 2.x는 Boot 4에서 안 돌아요.

## 환경변수

| 이름 | 뜻 | 로컬 기본값 |
|---|---|---|
| `PORT` | 서버 포트 | 8080 |
| `DATABASE_URL` | JDBC 주소 | `jdbc:mysql://localhost:3307/local_demand` |
| `DB_USERNAME`, `DB_PASSWORD` | DB 계정 | `local_demand` |
| `CORS_ALLOWED_ORIGINS` | 프론트 주소(쉼표로 구분) | `http://localhost:5173,http://localhost:3000` |
| `DEMO_LOGIN_ENABLED` | 시연용 로그인 켜기 | true |
| `DEMO_SEED_ENABLED` | 시연용 예시 데이터 넣기(발표 서버에서만 true). 켤 때마다 시연 계정·예시 요청·예시 선주문 제안을 처음 상태로 | false |
| `DEMO_RESET_GUEST_DATA` | `DEMO_SEED_ENABLED=true`일 때 손님 계정의 요청·제안·선주문·가게도 지우기(계정은 남음). 리허설·결선 직전에만 true | false |
| `KAKAO_REST_API_KEY`, `KAKAO_REDIRECT_URI` | 카카오 로그인(MVP에서는 안 씀) | 없음 |
| `AI_PROVIDER` | `fake`(AI 없이 낱말 맞추기) 또는 `openai` | fake |
| `AI_API_KEY` | OpenAI 키 | 없음 |
| `AI_BASE_URL` | API 주소 | `https://api.openai.com/v1` |
| `AI_MODEL` | 모델 이름 | `gpt-6-luna` |
| `AI_REASONING_EFFORT` | `reasoning_effort` 값. 비우면 안 보냄 | none |
| `AI_TIMEOUT_SECONDS` | AI 응답 기다리는 시간(초) | 15 |
| `AI_JSON_MODE` | `response_format: json_object`를 보낼지 | true |
| `JWT_SECRET` | 토큰(JWT) 도장 키. base64, 32바이트 이상 | 없음(비우면 서버가 켜질 때마다 임시 키) |

- `bootRun`은 Docker의 MySQL 접속 정보를 먼저 써요. 배포 DB에 붙여 보려면 `SPRING_DOCKER_COMPOSE_ENABLED=false`를 함께 줘요.
- 배포할 때는 `./gradlew bootJar`로 만든 `build/libs/local-demand-map-0.0.1-SNAPSHOT.jar`를 `java -jar`로 실행하고, 위 값을 서버 환경변수로 넣어요.
- `JWT_SECRET`을 비워 두면 서버를 다시 켤 때마다 키가 바뀌어 전에 받은 토큰이 무효가 돼요(로컬은 `/auth/guest`로 다시 받으면 돼요). 내 컴퓨터에서도 유지하고 싶거나 배포할 때는 키를 만들어 `.env`나 서버 환경변수에 넣어요. 키는 각자 만들고, 단톡방·커밋·캡처에 올리지 않아요.
  - Windows PowerShell: `$b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`
  - macOS·Linux: `openssl rand -base64 32`
