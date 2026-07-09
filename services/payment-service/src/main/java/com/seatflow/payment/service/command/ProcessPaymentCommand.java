package com.seatflow.payment.service.command;

import com.seatflow.payment.domain.PaymentMethod;

import java.math.BigDecimal;

public record ProcessPaymentCommand(
        Long reservationId,
        String userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        Long couponId
) {}