-- 처음 만드는 테이블. API 명세 초안 v0의 "4. 데이터 모델"을 따라요.
-- main에 합친 마이그레이션은 고치지 말고, 바꿀 게 있으면 V3__설명.sql 처럼 새 파일을 더해요.
-- JPA에서 다루기 쉽게 모든 테이블에 id를 두고, 명세의 "하나만" 규칙은 고유 제약으로 걸었어요.

-- 용인시 행정동
CREATE TABLE dong (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    adm_code    VARCHAR(10) NOT NULL COMMENT '행정동 코드',
    name        VARCHAR(30) NOT NULL,
    gu          VARCHAR(10) NOT NULL COMMENT '처인구 / 기흥구 / 수지구',
    center_lat  DOUBLE      NOT NULL,
    center_lon  DOUBLE      NOT NULL,
    boundary    LONGTEXT    NULL COMMENT '경계 GeoJSON 문자열 그대로',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dong_adm_code (adm_code)
);

-- 업종
CREATE TABLE category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30) NOT NULL COMMENT '예: FRUIT_VEG',
    name        VARCHAR(30) NOT NULL COMMENT '화면 이름. 예: 과일·채소 가게',
    sbiz_name   VARCHAR(50) NOT NULL COMMENT '상가정보 업종 이름. 예: 채소/과일 소매업',
    mart_check  BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '대형마트가 대신할 수 있는 식품 업종인지',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (code)
);

-- 용인시 대규모 점포 중 대형마트
CREATE TABLE mart (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    name  VARCHAR(100) NOT NULL,
    lat   DOUBLE       NOT NULL,
    lon   DOUBLE       NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE app_user (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    kakao_id    BIGINT      NULL COMMENT '카카오 회원번호. 시연 계정은 NULL',
    nickname    VARCHAR(30) NOT NULL,
    role        VARCHAR(20) NULL COMMENT 'RESIDENT / OWNER / FOUNDER. 역할을 고르기 전에는 NULL',
    dong_id     BIGINT      NULL COMMENT '주민 위치는 동까지만 저장',
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_kakao (kakao_id),
    CONSTRAINT fk_user_dong FOREIGN KEY (dong_id) REFERENCES dong (id)
);

-- 사장님 가게 (사장님 한 명에 가게 하나)
CREATE TABLE owner_shop (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    shop_name    VARCHAR(50) NOT NULL,
    category_id  BIGINT      NOT NULL,
    lat          DOUBLE      NOT NULL,
    lon          DOUBLE      NOT NULL,
    dong_id      BIGINT      NULL COMMENT '좌표로 서버가 찾은 동',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_shop_user (user_id),
    CONSTRAINT fk_owner_shop_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_owner_shop_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_owner_shop_dong FOREIGN KEY (dong_id) REFERENCES dong (id)
);

-- 상가정보에서 불러온 기존 가게
CREATE TABLE store (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    source_id    VARCHAR(30)  NOT NULL COMMENT '상가정보 상가업소번호',
    name         VARCHAR(100) NOT NULL,
    category_id  BIGINT       NOT NULL,
    lat          DOUBLE       NOT NULL,
    lon          DOUBLE       NOT NULL,
    dong_id      BIGINT       NULL COMMENT '용인시 동 경계 밖이면 NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_source (source_id),
    KEY idx_store_dong_category (dong_id, category_id),
    CONSTRAINT fk_store_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_store_dong FOREIGN KEY (dong_id) REFERENCES dong (id)
);

-- 빈 업종 (서버가 미리 계산, 명세 7번)
CREATE TABLE gap (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    dong_id          BIGINT      NOT NULL,
    category_id      BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL COMMENT 'MISSING / MART_NEAR / EXISTS',
    walk_shop_count  INT         NOT NULL COMMENT '동 중심 1km 안 같은 업종 가게 수',
    nearest_mart_id  BIGINT      NULL,
    nearest_mart_km  DOUBLE      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gap_dong_category (dong_id, category_id),
    CONSTRAINT fk_gap_dong FOREIGN KEY (dong_id) REFERENCES dong (id),
    CONSTRAINT fk_gap_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_gap_mart FOREIGN KEY (nearest_mart_id) REFERENCES mart (id)
);

-- 주민 요청. 같은 사람이 같은 동·같은 업종을 두 번 요청하지 못하게 DB가 막아요.
CREATE TABLE shop_request (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    dong_id      BIGINT       NOT NULL,
    category_id  BIGINT       NOT NULL,
    text         VARCHAR(200) NULL COMMENT '주민이 쓴 문장(200자 이내)',
    conditions   VARCHAR(200) NOT NULL DEFAULT '' COMMENT '조건 코드를 쉼표로 이어 씀. 예: LATE_OPEN,SMALL_QTY',
    time_slots   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '시간대 코드를 쉼표로 이어 씀. 예: WEEKDAY_EVENING,WEEKEND',
    frequency    VARCHAR(20)  NULL COMMENT 'WEEKLY_PLUS / MONTHLY_2_3 / SOMETIMES',
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_user_dong_category (user_id, dong_id, category_id),
    KEY idx_request_dong_category (dong_id, category_id),
    CONSTRAINT fk_request_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_request_dong FOREIGN KEY (dong_id) REFERENCES dong (id),
    CONSTRAINT fk_request_category FOREIGN KEY (category_id) REFERENCES category (id)
);

-- AI 요약 문장. 같은 숫자(input_hash)로는 AI를 다시 부르지 않고 여기 저장한 문장을 써요.
CREATE TABLE ai_summary (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    dong_id      BIGINT        NOT NULL,
    category_id  BIGINT        NOT NULL,
    kind         VARCHAR(20)   NOT NULL COMMENT '어느 화면의 문장인지. 예: OWNER_DEMAND(화면 5), FOUNDER_REPORT(화면 7)',
    input_hash   VARCHAR(64)   NOT NULL COMMENT 'AI에 넣은 숫자 JSON의 SHA-256',
    text         VARCHAR(1000) NOT NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_summary_input (dong_id, category_id, kind, input_hash),
    CONSTRAINT fk_ai_summary_dong FOREIGN KEY (dong_id) REFERENCES dong (id),
    CONSTRAINT fk_ai_summary_category FOREIGN KEY (category_id) REFERENCES category (id)
);
