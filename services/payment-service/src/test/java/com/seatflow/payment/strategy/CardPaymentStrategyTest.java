package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CardPaymentStrategyTest {

    private final CardPaymentStrategy strategy = new CardPaymentStrategy();

    @Test
    @DisplayName("supportedMethod()는 CREDIT_CARD를 반환한다")
    void supportsCreditCard() {
        assertThat(strategy.supportedMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
    }

    @Test
    @DisplayName("process()는 항상 true를 반환한다 (Mock 구현)")
    void processAlwaysReturnsTrue() {
        assertThat(strategy.process("payment-1", BigDecimal.valueOf(50000))).isTrue();
    }

    @Test
    @DisplayName("refund()는 항상 true를 반환한다 (Mock 구현)")
    void refundAlwaysReturnsTrue() {
        assertThat(strategy.refund("payment-1", BigDecimal.valueOf(45000))).isTrue();
    }
}