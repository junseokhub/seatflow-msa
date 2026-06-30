-- 결제 멱등성(비즈니스 레벨): 한 예매에 COMPLETED 결제는 하나만 허용한다.
-- status가 COMPLETED일 때만 reservation_id 값을 갖고, 그 외(PENDING/FAILED)는 NULL인
-- 생성 컬럼을 둔다. NULL은 UNIQUE 제약에서 중복이 허용되므로, 실패한 결제는 여러 번
-- 재시도(여러 FAILED 행)할 수 있고, 성공(COMPLETED)은 예매당 한 번만 가능하다.
ALTER TABLE payments
    ADD COLUMN completed_reservation_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'COMPLETED' THEN reservation_id ELSE NULL END
            ) STORED;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_completed_reservation
        UNIQUE (completed_reservation_id);