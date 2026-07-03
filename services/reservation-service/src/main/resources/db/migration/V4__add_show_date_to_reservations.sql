-- 예매에 공연 일시(show_date)를 둔다. seat.held로 받은 값을 저장하고,
-- 취소 마감·수수료 계산의 근거로 쓴다. 단발성 공연(1회)을 가정하며,
-- 회차별 공연은 향후 회차(schedule) 모델로 확장한다.
ALTER TABLE reservations
    ADD COLUMN show_date DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);