-- 좌석에 공연일(show_date)을 둔다. show.created로 받은 값을 저장하고,
-- 예매(seat.held) 시 reservation에 전파해 취소 마감 계산의 근거로 쓴다.
-- 기존 좌석이 있다면 임시로 현재 시각을 채운다(신규 환경이면 무의미).
ALTER TABLE seats
    ADD COLUMN show_date DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);