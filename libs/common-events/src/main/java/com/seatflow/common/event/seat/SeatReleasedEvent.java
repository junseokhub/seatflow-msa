package com.seatflow.common.event.seat;

import com.seatflow.common.event.VersionedEvent;

/**
 * 좌석 반환 완료 응답. seat이 SeatReleaseCommand를 처리한 뒤 오케스트레이터에게 돌려준다.
 */
public record SeatReleasedEvent(
        Long sagaId,
        Long reservationId,
        Long seatId
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}