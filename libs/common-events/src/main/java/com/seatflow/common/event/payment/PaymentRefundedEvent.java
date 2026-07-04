package com.seatflow.common.event.payment;

import java.math.BigDecimal;

/**
 * 환불 성공 응답.
 */
public record PaymentRefundedEvent(
        Long sagaId,
        Long reservationId,
        BigDecimal refundedAmount
) {}