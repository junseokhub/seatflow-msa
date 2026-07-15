-- 취소 Saga 상태. 예매 취소 흐름(좌석 반환 ->환불 ->완료, 실패 시 보상)을 추적한다.
-- reservation_id unique로 같은 예매의 취소 Saga 중복 생성을 막는다.
CREATE TABLE cancel_saga (
     id             BIGINT         NOT NULL AUTO_INCREMENT,
     reservation_id BIGINT         NOT NULL,
     user_id        VARCHAR(64)    NOT NULL,
     seat_id        BIGINT         NOT NULL,
     show_id        VARCHAR(64)    NOT NULL,
     refund_amount  DECIMAL(10, 2) NOT NULL,
     status         VARCHAR(20)    NOT NULL,
     created_at     DATETIME(6)    NOT NULL,
     updated_at     DATETIME(6)    NOT NULL,
     PRIMARY KEY (id),
     CONSTRAINT uk_cancel_saga_reservation UNIQUE (reservation_id),
     INDEX idx_cancel_saga_status (status)
);