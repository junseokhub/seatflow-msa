CREATE TABLE coupon_campaigns (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(255)   NOT NULL,
    discount_amount  DECIMAL(10,2)  NOT NULL,
    total_quantity   INT            NOT NULL,
    issued_quantity  INT            NOT NULL DEFAULT 0,
    created_at       DATETIME(6)    NOT NULL,
    expires_at       DATETIME(6)    NULL
);

CREATE TABLE coupons (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id      BIGINT         NOT NULL,
    user_id          VARCHAR(64)    NOT NULL,
    discount_amount  DECIMAL(10,2)  NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    reservation_id   BIGINT         NULL,
    issued_at        DATETIME(6)    NULL,
    used_at          DATETIME(6)    NULL,
    CONSTRAINT uk_coupon_campaign_user UNIQUE (campaign_id, user_id)
    );

CREATE INDEX idx_coupons_user_id ON coupons (user_id);
CREATE INDEX idx_coupons_reservation_id ON coupons (reservation_id);