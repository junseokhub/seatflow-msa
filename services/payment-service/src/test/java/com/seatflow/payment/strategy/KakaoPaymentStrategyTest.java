package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoPaymentStrategyTest {

    private final KakaoPaymentStrategy strategy = new KakaoPaymentStrategy();

    @Test
    @DisplayName("supportedMethod()는 KAKAO를 반환한다")
    void supportsKakao() {
        assertThat(strategy.supportedMethod()).isEqualTo(PaymentMethod.KAKAO);
    }

    @Test
    @DisplayName("process()는 항상 true를 반환한다 (Mock 구현)")
    void processAlwaysReturnsTrue() {
        boolean result = strategy.process("payment-1", BigDecimal.valueOf(50000));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("refund()는 항상 true를 반환한다 (Mock 구현)")
    void refundAlwaysReturnsTrue() {
        boolean result = strategy.refund("payment-1", BigDecimal.valueOf(45000));

        assertThat(result).isTrue();
    }
}