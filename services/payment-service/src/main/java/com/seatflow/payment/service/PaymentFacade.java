package com.seatflow.payment.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.redis.IdempotencyProvider;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제의 인프라 레벨 멱등성(1층)을 담당하는 진입점.
 * Redis 키 선점(트랜잭션 밖)으로 같은 Idempotency-Key의 광클·재시도를 차단한 뒤,
 * 결제 본체(@Transactional)를 호출한다. Redis 작업을 트랜잭션과 분리하기 위해
 * 결제 트랜잭션을 가진 PaymentService와 별도 빈으로 둔다(self-invocation 회피).
 */
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final IdempotencyProvider idempotencyProvider;

    public Payment pay(String idempotencyKey, ProcessPaymentCommand command) {
        long acquired = idempotencyProvider.tryAcquire(idempotencyKey);
        if (acquired != 1) {
            // 0=처리 중, -1=이미 완료 ->중복 요청 거절
            throw new BusinessException(
                    PaymentErrorCode.DUPLICATE_REQUEST.getStatus().value(),
                    PaymentErrorCode.DUPLICATE_REQUEST.getMessage());
        }
        try {
            Payment payment = paymentService.executePayment(command);
            idempotencyProvider.markDone(idempotencyKey);
            return payment;
        } catch (RuntimeException e) {
            // 결제 실패/거절 시 키 해제 ->동일 키로 재시도 허용
            idempotencyProvider.release(idempotencyKey);
            throw e;
        }
    }
}