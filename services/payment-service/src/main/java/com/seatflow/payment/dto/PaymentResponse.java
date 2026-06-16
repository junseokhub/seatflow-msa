package com.seatflow.payment.dto;

import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentMethod;
import com.seatflow.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        String paymentNumber,
        Long reservationId,
        String userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getReservationId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}