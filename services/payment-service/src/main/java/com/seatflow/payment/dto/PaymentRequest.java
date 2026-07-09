package com.seatflow.payment.dto;

import com.seatflow.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull Long reservationId,
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod paymentMethod,
        Long couponId
) {}