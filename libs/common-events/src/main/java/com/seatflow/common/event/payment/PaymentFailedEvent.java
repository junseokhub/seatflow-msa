package com.seatflow.common.event.payment;

import java.math.BigDecimal;

public record PaymentFailedEvent(
        Long reservationId,
        String userId,
        BigDecimal amount
) {}