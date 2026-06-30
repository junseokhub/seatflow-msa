package com.seatflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.common.event.payment.PaymentFailedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.payment.domain.Outbox;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentStatus;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.repository.OutboxRepository;
import com.seatflow.payment.repository.PaymentRepository;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import com.seatflow.payment.strategy.PaymentStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPaymentService implements PaymentService {

    private static final String SOURCE = "payment-service";

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper kafkaObjectMapper;
    private final PaymentStrategyRegistry strategyRegistry;

    /**
     * 결제 처리.
     *
     * 비즈니스 멱등성(이미 성공한 예매의 중복 결제 차단)은 2단계로 막는다.
     *  1) 사전 확인: 이미 COMPLETED 결제가 있으면 곧바로 예외(불필요한 결제 시도 방지).
     *  2) 최종 방어: COMPLETED 저장 시 payments의 부분 unique 제약(completed_reservation_id)이
     *     동시 요청을 원자적으로 막는다. 사전 확인과 저장 사이의 틈(check-then-act)은
     *     이 제약이 닫는다. 충돌은 PAYMENT_ALREADY_COMPLETED로 변환한다.
     *
     * 실패(FAILED)는 제약 대상이 아니므로(컬럼 NULL) 다른 수단으로 재시도할 수 있다.
     */
    @Override
    @Transactional
    public Payment processPayment(ProcessPaymentCommand command) {
        // 1) 사전 확인 — 이미 완료된 결제가 있으면 빠르게 거절
        paymentRepository
                .findByReservationIdAndStatus(command.reservationId(), PaymentStatus.COMPLETED)
                .ifPresent(p -> {
                    throw new BusinessException(
                            PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getStatus().value(),
                            PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
                });

        Payment payment = Payment.builder()
                .reservationId(command.reservationId())
                .userId(command.userId())
                .amount(command.amount())
                .paymentMethod(command.paymentMethod())
                .build();
        paymentRepository.save(payment);

        boolean success = strategyRegistry
                .get(command.paymentMethod())
                .process(payment.getPaymentNumber(), command.amount());

        if (success) {
            payment.complete();
            // 2) 최종 방어 — COMPLETED 전이 시 부분 unique 충돌이면 동시 중복 결제
            try {
                paymentRepository.flush();   // unique 제약을 지금 강제로 확인(커밋까지 미루지 않음)
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(
                        PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getStatus().value(),
                        PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
            }
            appendOutbox(EventTopic.PAYMENT_COMPLETED, command.userId(),
                    new PaymentCompletedEvent(
                            command.reservationId(), command.userId(), command.amount()));
            log.info("Payment completed: paymentNumber={}", payment.getPaymentNumber());
        } else {
            payment.fail();
            appendOutbox(EventTopic.PAYMENT_FAILED, command.userId(),
                    new PaymentFailedEvent(
                            command.reservationId(), command.userId(), command.amount()));
            log.info("Payment failed: paymentNumber={}", payment.getPaymentNumber());
        }

        return payment;
    }

    private void appendOutbox(String eventType, String messageKey, Object event) {
        EventEnvelope<?> envelope = EventEnvelope.of(eventType, SOURCE, event);
        outboxRepository.save(Outbox.builder()
                .eventId(envelope.eventId())
                .eventType(eventType)
                .messageKey(messageKey)
                .payload(toJson(envelope))
                .build());
    }

    private String toJson(Object o) {
        try {
            return kafkaObjectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException(
                    PaymentErrorCode.INTERNAL_ERROR.getStatus().value(),
                    PaymentErrorCode.INTERNAL_ERROR.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        PaymentErrorCode.PAYMENT_NOT_FOUND.getStatus().value(),
                        PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage()
                ));
    }
}