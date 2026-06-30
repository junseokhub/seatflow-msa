-- 예매에 좌석 원가(amount)를 둔다. seat.held로 전달된 서버측 가격을 저장하며,
-- 결제 시 클라이언트가 보낸 금액이 아니라 이 값을 결제 금액의 근거로 사용한다.
ALTER TABLE reservations
    ADD COLUMN amount DECIMAL(10, 2) NOT NULL DEFAULT 0;