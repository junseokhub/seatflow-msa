// 기존 각 구현체(Card/Kakao 등)에 refund()를 추가한다. CARD 예시:

package com.seatflow.payment.strategy;

import com.seatflow.payment.domain.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class CardPaymentStrategy implements PaymentStrategy {

    @Override
    public boolean process(String paymentNumber, BigDecimal amount) {
        log.info("[Card] approve: paymentNumber={}, amount={}", paymentNumber, amount);
        return true;
    }

    @Override
    public boolean refund(String paymentNumber, BigDecimal refundAmount) {
        log.info("[Card] refund: paymentNumber={}, refundAmount={}", paymentNumber, refundAmount);
        return true;
    }

    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.CREDIT_CARD;
    }
}