package com.seatflow.common.event.payment;

import com.seatflow.common.event.VersionedEvent;

import java.math.BigDecimal;

public record PaymentFailedEvent(
        Long reservationId,
        String userId,
        BigDecimal amount
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}