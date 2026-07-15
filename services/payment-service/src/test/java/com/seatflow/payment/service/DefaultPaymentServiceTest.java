package com.seatflow.payment.service;

import com.seatflow.common.client.CouponClient;
import com.seatflow.common.client.ReservationClient;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.common.response.ApiResponse;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentMethod;
import com.seatflow.payment.domain.PaymentStatus;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.repository.PaymentRepository;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import com.seatflow.payment.strategy.PaymentStrategy;
import com.seatflow.payment.strategy.PaymentStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentStrategyRegistry strategyRegistry;
    @Mock
    private ReservationClient reservationClient;
    @Mock
    private CouponClient couponClient;
    @Mock
    private OutboxAppender outboxAppender;
    @Mock
    private PaymentStrategy paymentStrategy;

    private DefaultPaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new DefaultPaymentService(
                paymentRepository, strategyRegistry, reservationClient, couponClient, outboxAppender);
    }

    private ReservationClient.ReservationView pendingReservationView(BigDecimal amount) {
        return new ReservationClient.ReservationView(1L, "user1", "PENDING", amount);
    }

    private ProcessPaymentCommand command(BigDecimal amount, Long couponId) {
        return new ProcessPaymentCommand(1L, "user1", amount, PaymentMethod.CREDIT_CARD, couponId);
    }

    @Nested
    @DisplayName("executePayment() - 사전 검증")
    class PreValidation {

        @Test
        @DisplayName("예매가 PENDING이 아니면 RESERVATION_NOT_PAYABLE 예외를 던진다")
        void throwsWhenReservationNotPending() {
            var confirmedView = new ReservationClient.ReservationView(
                    1L, "user1", "CONFIRMED", BigDecimal.valueOf(50000));
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(confirmedView));

            assertThatThrownBy(() -> paymentService.executePayment(command(BigDecimal.valueOf(50000), null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.RESERVATION_NOT_PAYABLE.getMessage());
        }

        @Test
        @DisplayName("요청 금액이 예매 금액과 다르면 AMOUNT_MISMATCH 예외를 던진다")
        void throwsWhenAmountMismatch() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));

            assertThatThrownBy(() -> paymentService.executePayment(command(BigDecimal.valueOf(40000), null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.AMOUNT_MISMATCH.getMessage());
        }

        @Test
        @DisplayName("이미 완료된 결제가 있으면 PAYMENT_ALREADY_COMPLETED 예외를 던진다")
        void throwsWhenAlreadyCompleted() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.of(Payment.builder().status(PaymentStatus.COMPLETED).build()));

            assertThatThrownBy(() -> paymentService.executePayment(command(BigDecimal.valueOf(50000), null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
        }

        @Test
        @DisplayName("예매 조회 자체가 실패하면 RESERVATION_NOT_FOUND 예외를 던진다")
        void throwsWhenReservationLookupFails() {
            given(reservationClient.getReservation(1L)).willThrow(new RuntimeException("network error"));

            assertThatThrownBy(() -> paymentService.executePayment(command(BigDecimal.valueOf(50000), null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.RESERVATION_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("executePayment() - 쿠폰 없는 정상 흐름")
    class SuccessWithoutCoupon {

        @Test
        @DisplayName("PG 성공 시 결제가 COMPLETED되고 payment.completed 이벤트가 발행된다")
        void completesPaymentAndPublishesEvent() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.process(anyString(), any())).willReturn(true);

            Payment result = paymentService.executePayment(command(BigDecimal.valueOf(50000), null));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
            verify(couponClient, never()).confirmCoupon(any(), any(), any());
        }

        @Test
        @DisplayName("PG 실패 시 결제가 FAILED되고 payment.failed 이벤트가 발행된다")
        void failsPaymentAndPublishesFailedEvent() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.process(anyString(), any())).willReturn(false);

            Payment result = paymentService.executePayment(command(BigDecimal.valueOf(50000), null));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("PG 성공 후 flush에서 unique 제약 위반이 나면 PAYMENT_ALREADY_COMPLETED 예외를 던진다 (동시 중복결제 방어)")
        void throwsWhenFlushViolatesUniqueConstraint() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.process(anyString(), any())).willReturn(true);
            doThrow(new DataIntegrityViolationException("duplicate"))
                    .when(paymentRepository).flush();

            assertThatThrownBy(() -> paymentService.executePayment(command(BigDecimal.valueOf(50000), null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
        }
    }

    @Nested
    @DisplayName("executePayment() - 쿠폰 있는 흐름")
    class WithCoupon {

        @Test
        @DisplayName("쿠폰이 유효하면 할인 적용된 금액으로 PG를 호출하고, 성공 시 쿠폰을 확정한다")
        void appliesDiscountAndConfirmsCouponOnSuccess() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            var couponView = new CouponClient.CouponValidationView(10L, true, BigDecimal.valueOf(5000));
            given(couponClient.validateCoupon(10L, "user1")).willReturn(ApiResponse.ok(couponView));
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.process(anyString(), any())).willReturn(true);

            // 원래 예매 금액(50000)을 그대로 커맨드에 넣어도, 서버가 쿠폰 검증 후
            // 자체적으로 discountAmount를 빼서 최종 PG 호출 금액(45000)을 계산한다.
            // 커맨드의 amount는 "결제 요청 시점에 클라이언트가 알던 예매 원가"와
            // 일치해야 하는 검증 대상이지, 최종 결제액이 아니다.
            Payment result = paymentService.executePayment(command(BigDecimal.valueOf(50000), 10L));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.getAmount()).isEqualByComparingTo("45000");   // 50000 - 5000
            assertThat(result.getDiscountAmount()).isEqualByComparingTo("5000");
            verify(paymentStrategy).process(anyString(), eq(BigDecimal.valueOf(45000)));
            verify(couponClient).confirmCoupon(10L, "user1", 1L);
        }

        @Test
        @DisplayName("쿠폰이 유효하지 않으면 COUPON_INVALID 예외를 던지고 PG를 호출하지 않는다")
        void throwsWhenCouponInvalid() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            var invalidCoupon = new CouponClient.CouponValidationView(10L, false, BigDecimal.ZERO);
            given(couponClient.validateCoupon(10L, "user1")).willReturn(ApiResponse.ok(invalidCoupon));

            assertThatThrownBy(() -> paymentService.executePayment(command(BigDecimal.valueOf(50000), 10L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.COUPON_INVALID.getMessage());

            verify(strategyRegistry, never()).get(any());
        }

        @Test
        @DisplayName("쿠폰 검증 호출 자체가 실패해도 COUPON_INVALID로 처리된다")
        void throwsCouponInvalidWhenValidationCallFails() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            given(couponClient.validateCoupon(10L, "user1")).willThrow(new RuntimeException("network error"));

            assertThatThrownBy(() -> paymentService.executePayment(command(BigDecimal.valueOf(50000), 10L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.COUPON_INVALID.getMessage());
        }

        @Test
        @DisplayName("결제는 성공했는데 쿠폰 확정이 실패해도, 결제 자체는 롤백되지 않고 COMPLETED로 남는다")
        void paymentStaysCompletedEvenIfCouponConfirmFails() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            var couponView = new CouponClient.CouponValidationView(10L, true, BigDecimal.valueOf(5000));
            given(couponClient.validateCoupon(10L, "user1")).willReturn(ApiResponse.ok(couponView));
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.process(anyString(), any())).willReturn(true);
            doThrow(new RuntimeException("coupon service down"))
                    .when(couponClient).confirmCoupon(any(), any(), any());

            Payment result = paymentService.executePayment(command(BigDecimal.valueOf(50000), 10L));

            // 쿠폰 확정 실패가 예외로 전파되지 않고, 결제 자체는 COMPLETED로 정상 반환된다.
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("PG가 실패하면 쿠폰 확정 자체를 시도하지 않는다 (승인 실패했는데 쿠폰만 소진되는 것 방지)")
        void doesNotConfirmCouponWhenPgFails() {
            given(reservationClient.getReservation(1L))
                    .willReturn(ApiResponse.ok(pendingReservationView(BigDecimal.valueOf(50000))));
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            var couponView = new CouponClient.CouponValidationView(10L, true, BigDecimal.valueOf(5000));
            given(couponClient.validateCoupon(10L, "user1")).willReturn(ApiResponse.ok(couponView));
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.process(anyString(), any())).willReturn(false);   // PG 거절

            Payment result = paymentService.executePayment(command(BigDecimal.valueOf(50000), 10L));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(couponClient, never()).confirmCoupon(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getPayment()")
    class GetPayment {

        @Test
        @DisplayName("존재하는 결제를 반환한다")
        void returnsExistingPayment() {
            Payment payment = Payment.builder().status(PaymentStatus.COMPLETED).build();
            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

            Payment result = paymentService.getPayment(1L);

            assertThat(result).isEqualTo(payment);
        }

        @Test
        @DisplayName("존재하지 않으면 PAYMENT_NOT_FOUND 예외를 던진다")
        void throwsWhenNotFound() {
            given(paymentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("executeRefund()")
    class ExecuteRefund {

        @Test
        @DisplayName("COMPLETED 결제를 환불 성공시키면 REFUNDED 되고 payment.refunded 이벤트가 발행된다")
        void refundsCompletedPaymentSuccessfully() {
            Payment payment = Payment.builder()
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.COMPLETED)
                    .build();
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.of(payment));
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.refund(anyString(), any())).willReturn(true);

            paymentService.executeRefund(100L, 1L, BigDecimal.valueOf(45000));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("PG 환불이 거절되면 payment.refund.failed 이벤트가 발행되고 결제는 여전히 COMPLETED다")
        void publishesFailedEventWhenPgRefundRejected() {
            Payment payment = Payment.builder()
                    .paymentMethod(PaymentMethod.TOSS)   // Toss는 refund가 항상 false(Mock)
                    .status(PaymentStatus.COMPLETED)
                    .build();
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.of(payment));
            given(strategyRegistry.get(PaymentMethod.TOSS)).willReturn(paymentStrategy);
            given(paymentStrategy.refund(anyString(), any())).willReturn(false);

            paymentService.executeRefund(100L, 1L, BigDecimal.valueOf(45000));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);   // 안 바뀜
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("결제 내역 자체가 없으면 payment.refund.failed를 발행한다")
        void publishesFailedEventWhenPaymentNotFound() {
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.REFUNDED))
                    .willReturn(Optional.empty());

            paymentService.executeRefund(100L, 1L, BigDecimal.valueOf(45000));

            verify(outboxAppender).append(any(), anyString(), anyString(), any());
            verify(strategyRegistry, never()).get(any());
        }

        @Test
        @DisplayName("이미 REFUNDED인 결제로 중복 환불 명령이 오면 PG를 다시 호출하지 않고 성공 응답만 재발행한다 (at-least-once 대응)")
        void republishesSuccessForAlreadyRefundedPayment() {
            Payment refundedPayment = Payment.builder()
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .status(PaymentStatus.REFUNDED)
                    .build();
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.empty());
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.REFUNDED))
                    .willReturn(Optional.of(refundedPayment));

            paymentService.executeRefund(100L, 1L, BigDecimal.valueOf(45000));

            verify(strategyRegistry, never()).get(any());   // PG 재호출 안 함
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("쿠폰을 썼던 결제의 환불이 성공하면 쿠폰도 함께 복원한다")
        void restoresCouponAfterSuccessfulRefundOfCouponPayment() {
            Payment payment = Payment.builder()
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .couponId(10L)
                    .status(PaymentStatus.COMPLETED)
                    .build();
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.of(payment));
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.refund(anyString(), any())).willReturn(true);

            paymentService.executeRefund(100L, 1L, BigDecimal.valueOf(45000));

            verify(couponClient).restoreCoupon(1L);
        }

        @Test
        @DisplayName("쿠폰 복원이 실패해도 환불 자체는 성공으로 처리되고 이벤트가 발행된다")
        void refundSucceedsEvenIfCouponRestoreFails() {
            Payment payment = Payment.builder()
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .couponId(10L)
                    .status(PaymentStatus.COMPLETED)
                    .build();
            given(paymentRepository.findByReservationIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .willReturn(Optional.of(payment));
            given(strategyRegistry.get(PaymentMethod.CREDIT_CARD)).willReturn(paymentStrategy);
            given(paymentStrategy.refund(anyString(), any())).willReturn(true);
            doThrow(new RuntimeException("coupon service down"))
                    .when(couponClient).restoreCoupon(1L);

            paymentService.executeRefund(100L, 1L, BigDecimal.valueOf(45000));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }
    }
}