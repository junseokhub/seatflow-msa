-- 실제 환불 금액(취소 수수료 반영)을 기록한다. 원 결제액(amount)과 별개다.
-- 환불 전에는 NULL이다.
ALTER TABLE payments
    ADD COLUMN refunded_amount DECIMAL(10, 2) NULL;