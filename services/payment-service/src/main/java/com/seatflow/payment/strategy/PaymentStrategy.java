package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;

import java.math.BigDecimal;

public interface PaymentStrategy {

    // 결제
    boolean process(String paymentNumber, BigDecimal amount);

    // 환불
    boolean refund(String paymentNumber, BigDecimal refundAmount);

    PaymentMethod supportedMethod();
}