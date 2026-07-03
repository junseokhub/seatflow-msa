package com.seatflow.common.event.reservation;

/**
 * 예매 확정 이벤트. 결제 완료로 예매가 CONFIRMED되면 발행한다.
 * seat이 받아 해당 좌석을 임시 점유(Redis hold)에서 영구 확정(DB RESERVED)으로 넘긴다.
 * 좌석 확정은 되돌릴 필요가 없는 정상 흐름이라, 보상이 필요한 Saga가 아닌 단순 이벤트로 잇는다.
 */
public record ReservationConfirmedEvent(
        Long reservationId,
        String userId,
        String showId,
        Long seatId
) {}