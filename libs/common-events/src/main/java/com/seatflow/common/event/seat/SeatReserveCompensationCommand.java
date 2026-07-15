package com.seatflow.common.event.seat;

import com.seatflow.common.event.VersionedEvent;

/**
 * 취소 Saga 보상 명령. 환불 실패로 좌석 반환을 되돌려 다시 점유(RESERVED) 시킨다.
 * 정방향 SeatReleaseCommand와 대칭 구조. seat.reserve()는 이미 멱등하게 설계돼 있어
 * 이 명령이 중복 도착해도 안전하다.
 */
public record SeatReserveCompensationCommand(
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