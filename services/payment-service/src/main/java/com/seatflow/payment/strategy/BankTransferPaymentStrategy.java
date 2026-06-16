package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class BankTransferPaymentStrategy implements PaymentStrategy {

    @Override
    public boolean process(String paymentNumber, BigDecimal amount) {
        log.info("[BankTransfer] Processing payment: paymentNumber={}, amount={}", paymentNumber, amount);
        return true; // Mock
    }

    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.BANK_TRANSFER;
    }
}