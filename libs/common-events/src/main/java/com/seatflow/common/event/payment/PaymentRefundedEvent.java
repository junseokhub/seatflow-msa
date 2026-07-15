package com.seatflow.common.event.payment;

import com.seatflow.common.event.VersionedEvent;

import java.math.BigDecimal;

/**
 * 환불 성공 응답.
 */
public record PaymentRefundedEvent(
        Long sagaId,
        Long reservationId,
        BigDecimal refundedAmount
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}