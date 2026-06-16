package com.seatflow.common.event.payment;

import java.math.BigDecimal;

public record PaymentCompletedEvent(
        Long reservationId,
        String userId,
        BigDecimal amount
) {}