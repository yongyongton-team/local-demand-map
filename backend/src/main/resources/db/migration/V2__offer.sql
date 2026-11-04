-- 선주문 수령 (API 명세 5-4). 채택할지 아직 정하는 중이에요.
-- 빼기로 하면 main에 합치기 전에 이 파일을 지워요. 합친 뒤라면 테이블을 지우는 새 마이그레이션을 더해요.

CREATE TABLE offer (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id      BIGINT       NOT NULL COMMENT '제안한 사장님(app_user.id)',
    dong_id       BIGINT       NOT NULL,
    category_id   BIGINT       NOT NULL,
    title         VARCHAR(50)  NOT NULL,
    description   VARCHAR(500) NULL,
    price         INT          NOT NULL COMMENT '원',
    pickup_at     DATETIME(6)  NOT NULL,
    pickup_place  VARCHAR(100) NOT NULL,
    min_orders    INT          NOT NULL COMMENT '1~100',
    deadline_at   DATETIME(6)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / CONFIRMED / CLOSED / CANCELLED / DONE',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_offer_dong_category (dong_id, category_id),
    CONSTRAINT fk_offer_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_offer_dong FOREIGN KEY (dong_id) REFERENCES dong (id),
    CONSTRAINT fk_offer_category FOREIGN KEY (category_id) REFERENCES category (id)
);

-- 한 사람은 한 제안에 한 번만 선주문해요 → ALREADY_ORDERED
CREATE TABLE pre_order (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    offer_id    BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    quantity    INT         NOT NULL DEFAULT 1,
    status      VARCHAR(20) NOT NULL DEFAULT 'RESERVED' COMMENT 'RESERVED / CANCELLED / PICKED_UP',
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_pre_order_offer_user (offer_id, user_id),
    CONSTRAINT fk_pre_order_offer FOREIGN KEY (offer_id) REFERENCES offer (id),
    CONSTRAINT fk_pre_order_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
