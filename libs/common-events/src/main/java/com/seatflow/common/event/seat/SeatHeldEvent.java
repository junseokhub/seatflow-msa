package com.seatflow.common.event.seat;

import java.math.BigDecimal;

/**
 * 좌석 점유 이벤트. 가격(price)을 함께 실어, 예매(reservation)가 결제 금액의 근거를
 * 서버 측 값으로 확보하게 한다. 클라이언트가 보낸 금액을 신뢰하지 않기 위함이다.
 */
public record SeatHeldEvent(
        String userId,
        String showId,
        Long seatId,
        BigDecimal price
) {}