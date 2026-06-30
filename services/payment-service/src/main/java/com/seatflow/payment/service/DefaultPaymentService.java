package com.seatflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.common.event.payment.PaymentFailedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.payment.domain.Outbox;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.repository.OutboxRepository;
import com.seatflow.payment.repository.PaymentRepository;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import com.seatflow.payment.strategy.PaymentStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 결제를 처리하고 결과 이벤트(payment.completed / payment.failed)를 Outbox에 적재한다.
     * 결제 저장과 이벤트 적재가 한 트랜잭션이라, 결제만 되고 이벤트가 유실되는 dual-write를
     * 방지한다. 실제 Kafka 발행은 공통 OutboxScheduler가 폴링으로 수행한다.
     */
    @Override
    @Transactional
    public Payment processPayment(ProcessPaymentCommand command) {
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

    /** 이벤트를 EventEnvelope로 감싸 직렬화한 뒤 Outbox에 적재한다(같은 트랜잭션). */
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