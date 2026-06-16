package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;

import java.math.BigDecimal;

public interface PaymentStrategy {
    boolean process(String paymentNumber, BigDecimal amount);
    PaymentMethod supportedMethod();
}