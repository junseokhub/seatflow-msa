-- 환불 후 쿠폰 복원이 아직 끝나지 않은 결제를 표시한다. 복원 호출이 실패하면 TRUE로 남아 재처리 대상이 된다.
ALTER TABLE payments
    ADD COLUMN coupon_restore_pending BOOLEAN NOT NULL DEFAULT FALSE;
