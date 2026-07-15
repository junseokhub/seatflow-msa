package com.seatflow.common.event.seat;

import com.seatflow.common.event.VersionedEvent;

/**
 * 좌석 재점유(보상) 완료 응답.
 */
public record SeatReservedCompensatedEvent(
        Long sagaId,
        Long reservationId,
        Long seatId
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}