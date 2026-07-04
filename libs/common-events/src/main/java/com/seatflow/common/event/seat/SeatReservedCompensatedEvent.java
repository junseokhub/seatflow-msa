package com.seatflow.common.event.seat;

/**
 * 좌석 재점유(보상) 완료 응답.
 */
public record SeatReservedCompensatedEvent(
        Long sagaId,
        Long reservationId,
        Long seatId
) {}