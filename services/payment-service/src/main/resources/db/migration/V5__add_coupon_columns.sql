ALTER TABLE payments
    ADD COLUMN coupon_id BIGINT NULL,
    ADD COLUMN discount_amount DECIMAL(10,2) NULL;