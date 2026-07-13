package com.seatflow.payment.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentMethod;
import com.seatflow.payment.domain.PaymentStatus;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.redis.IdempotencyProvider;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @Mock
    private PaymentService paymentService;
    @Mock
    private IdempotencyProvider idempotencyProvider;

    private PaymentFacade paymentFacade;

    @BeforeEach
    void setUp() {
        paymentFacade = new PaymentFacade(paymentService, idempotencyProvider);
    }

    private ProcessPaymentCommand command() {
        return new ProcessPaymentCommand(1L, "user1", BigDecimal.valueOf(50000),
                PaymentMethod.CREDIT_CARD, null);
    }

    private Payment payment() {
        return Payment.builder()
                .reservationId(1L).userId("user1").amount(BigDecimal.valueOf(50000))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    @Test
    @DisplayName("키 선점(1) 성공 시 결제를 실행하고 완료 후 markDone을 호출한다")
    void acquiresKeyAndProcessesPayment() {
        given(idempotencyProvider.tryAcquire("key-1")).willReturn(1L);
        given(paymentService.executePayment(any())).willReturn(payment());

        Payment result = paymentFacade.pay("key-1", command());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(idempotencyProvider).markDone("key-1");
        verify(idempotencyProvider, never()).release(any());
    }

    @Test
    @DisplayName("키 선점 실패(0=처리중)면 결제를 시도하지 않고 DUPLICATE_REQUEST 예외를 던진다")
    void throwsWhenKeyIsProcessing() {
        given(idempotencyProvider.tryAcquire("key-2")).willReturn(0L);

        assertThatThrownBy(() -> paymentFacade.pay("key-2", command()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PaymentErrorCode.DUPLICATE_REQUEST.getMessage());

        verify(paymentService, never()).executePayment(any());
    }

    @Test
    @DisplayName("키 선점 실패(-1=이미완료)면 결제를 시도하지 않고 DUPLICATE_REQUEST 예외를 던진다")
    void throwsWhenKeyAlreadyDone() {
        given(idempotencyProvider.tryAcquire("key-3")).willReturn(-1L);

        assertThatThrownBy(() -> paymentFacade.pay("key-3", command()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PaymentErrorCode.DUPLICATE_REQUEST.getMessage());

        verify(paymentService, never()).executePayment(any());
    }

    @Test
    @DisplayName("결제 실행 중 예외가 나면 키를 release해서 재시도를 허용하고 예외를 그대로 전파한다")
    void releasesKeyOnPaymentFailure() {
        given(idempotencyProvider.tryAcquire("key-4")).willReturn(1L);
        given(paymentService.executePayment(any()))
                .willThrow(new BusinessException(400, "결제 거절"));

        assertThatThrownBy(() -> paymentFacade.pay("key-4", command()))
                .isInstanceOf(BusinessException.class);

        verify(idempotencyProvider).release("key-4");
        verify(idempotencyProvider, never()).markDone(any());
    }
}