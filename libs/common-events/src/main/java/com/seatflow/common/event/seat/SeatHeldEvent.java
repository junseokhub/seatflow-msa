package com.seatflow.common.event.seat;

import com.seatflow.common.event.VersionedEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 좌석 점유 이벤트. 가격(price)과 공연일(showDate)을 함께 실어 reservation이
 * 결제 금액의 근거와 취소 마감 계산의 근거를 서버 측 값으로 확보하게 한다.
 */
public record SeatHeldEvent(
        String userId,
        String showId,
        Long seatId,
        BigDecimal price,
        LocalDateTime showDate
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}