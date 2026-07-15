package com.seatflow.common.event.seat;

import com.seatflow.common.event.VersionedEvent;

/**
 * 취소 Saga의 좌석 반환 명령. reservation(오케스트레이터)이 seat에 내린다.
 * sagaId로 어느 Saga의 명령인지 식별한다(reservationId는 도메인 키일 뿐 Saga 실행 식별자가 아니다).
 */
public record SeatReleaseCommand(
        Long sagaId,
        Long reservationId,
        String showId,
        Long seatId
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}