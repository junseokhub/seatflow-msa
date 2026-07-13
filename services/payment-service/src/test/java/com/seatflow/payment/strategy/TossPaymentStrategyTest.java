package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TossPaymentStrategy는 유일하게 refund()가 항상 false를 반환하는 Mock이다.
 * 환불 실패 시나리오를 시연하기 위한 의도적인 설계로 보인다. 이 특성 자체를 명시적으로 문서화하는 테스트다.
 */
class TossPaymentStrategyTest {

    private final TossPaymentStrategy strategy = new TossPaymentStrategy();

    @Test
    @DisplayName("supportedMethod()는 TOSS를 반환한다")
    void supportsToss() {
        assertThat(strategy.supportedMethod()).isEqualTo(PaymentMethod.TOSS);
    }

    @Test
    @DisplayName("process()는 항상 true를 반환한다 (Mock 구현)")
    void processAlwaysReturnsTrue() {
        assertThat(strategy.process("payment-1", BigDecimal.valueOf(50000))).isTrue();
    }

    @Test
    @DisplayName("refund()는 항상 false를 반환한다 (다른 전략들과 다른, 환불 실패 시연용 Mock)")
    void refundAlwaysReturnsFalse() {
        assertThat(strategy.refund("payment-1", BigDecimal.valueOf(45000))).isFalse();
    }
}