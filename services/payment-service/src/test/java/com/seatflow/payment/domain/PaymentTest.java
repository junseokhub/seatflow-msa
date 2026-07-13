package com.seatflow.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Payment paymentWithStatus(PaymentStatus status) {
        return Payment.builder()
                .reservationId(1L)
                .userId("user1")
                .amount(BigDecimal.valueOf(50000))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .couponId(null)
                .discountAmount(null)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("생성 직후 paymentNumber가 자동으로 채워진다")
    void paymentNumberIsGeneratedOnCreation() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);

        assertThat(payment.getPaymentNumber()).isNotBlank();
    }

    @Nested
    @DisplayName("complete()/fail()")
    class CompleteAndFail {

        @Test
        @DisplayName("complete()는 상태를 COMPLETED로 만든다")
        void completesPayment() {
            Payment payment = paymentWithStatus(PaymentStatus.PENDING);

            payment.complete();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("fail()은 상태를 FAILED로 만든다")
        void failsPayment() {
            Payment payment = paymentWithStatus(PaymentStatus.PENDING);

            payment.fail();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("COMPLETED 결제를 환불하면 REFUNDED로 전이되고 환불액이 기록된다")
        void refundsCompletedPayment() {
            Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);

            payment.refund(BigDecimal.valueOf(45000));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.getRefundedAmount()).isEqualByComparingTo("45000");
        }

        @Test
        @DisplayName("이미 REFUNDED면 멱등하게 무시한다 (환불액도 재기록하지 않음)")
        void refundIsIdempotent() {
            Payment payment = paymentWithStatus(PaymentStatus.REFUNDED);

            payment.refund(BigDecimal.valueOf(99999));   // 다른 금액으로 재시도

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.getRefundedAmount()).isNull();   // 원래 null이었던 그대로, 안 바뀜
        }

        @Test
        @DisplayName("COMPLETED가 아닌 결제(PENDING)는 환불할 수 없다")
        void cannotRefundNonCompletedPayment() {
            Payment payment = paymentWithStatus(PaymentStatus.PENDING);

            assertThatThrownBy(() -> payment.refund(BigDecimal.valueOf(45000)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("FAILED 결제는 환불할 수 없다")
        void cannotRefundFailedPayment() {
            Payment payment = paymentWithStatus(PaymentStatus.FAILED);

            assertThatThrownBy(() -> payment.refund(BigDecimal.valueOf(45000)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}