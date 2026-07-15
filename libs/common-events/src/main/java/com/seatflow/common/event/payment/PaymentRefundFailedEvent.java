package com.seatflow.common.event.payment;

import com.seatflow.common.event.VersionedEvent;

/**
 * 환불 실패 응답. 외부 PG 호출 실패·타임아웃 등으로 환불이 안 된 경우.
 * reason에 실패 사유를 담아 오케스트레이터가 로깅·모니터링할 수 있게 한다.
 * 오케스트레이터는 이 이벤트를 받으면 좌석 반환을 되돌리는 보상을 시작한다.
 */
public record PaymentRefundFailedEvent(
        Long sagaId,
        Long reservationId,
        String reason
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}