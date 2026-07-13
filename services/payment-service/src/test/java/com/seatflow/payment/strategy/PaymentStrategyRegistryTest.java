package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStrategyRegistryTest {

    private PaymentStrategy fakeStrategy(PaymentMethod method) {
        return new PaymentStrategy() {
            @Override
            public boolean process(String paymentNumber, BigDecimal amount) { return true; }
            @Override
            public boolean refund(String paymentNumber, BigDecimal refundAmount) { return true; }
            @Override
            public PaymentMethod supportedMethod() { return method; }
        };
    }

    @Test
    @DisplayName("등록된 전략을 결제 수단으로 정확히 조회한다")
    void getsRegisteredStrategyByMethod() {
        PaymentStrategy cardStrategy = fakeStrategy(PaymentMethod.CREDIT_CARD);
        PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(cardStrategy));

        PaymentStrategy result = registry.get(PaymentMethod.CREDIT_CARD);

        assertThat(result).isSameAs(cardStrategy);
    }

    @Test
    @DisplayName("등록되지 않은 결제 수단을 조회하면 IllegalArgumentException을 던진다")
    void throwsForUnsupportedMethod() {
        PaymentStrategyRegistry registry = new PaymentStrategyRegistry(
                List.of(fakeStrategy(PaymentMethod.CREDIT_CARD)));

        assertThatThrownBy(() -> registry.get(PaymentMethod.KAKAO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 전략 목록으로 생성하면 IllegalStateException을 던진다")
    void throwsWhenNoStrategiesProvided() {
        assertThatThrownBy(() -> new PaymentStrategyRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("null 전략 목록으로 생성하면 IllegalStateException을 던진다")
    void throwsWhenStrategiesIsNull() {
        assertThatThrownBy(() -> new PaymentStrategyRegistry(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("같은 결제 수단을 지원하는 전략이 중복 등록되면 IllegalStateException을 던진다")
    void throwsWhenDuplicateMethodRegistered() {
        PaymentStrategy first = fakeStrategy(PaymentMethod.CREDIT_CARD);
        PaymentStrategy duplicate = fakeStrategy(PaymentMethod.CREDIT_CARD);

        assertThatThrownBy(() -> new PaymentStrategyRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("여러 결제 수단이 각각 정확히 등록되고 조회된다")
    void registersMultipleDistinctMethods() {
        PaymentStrategy card = fakeStrategy(PaymentMethod.CREDIT_CARD);
        PaymentStrategy kakao = fakeStrategy(PaymentMethod.KAKAO);
        PaymentStrategy toss = fakeStrategy(PaymentMethod.TOSS);
        PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(card, kakao, toss));

        assertThat(registry.get(PaymentMethod.CREDIT_CARD)).isSameAs(card);
        assertThat(registry.get(PaymentMethod.KAKAO)).isSameAs(kakao);
        assertThat(registry.get(PaymentMethod.TOSS)).isSameAs(toss);
    }
}