package com.seatflow.payment.service;

import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.common.event.payment.PaymentFailedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.repository.PaymentRepository;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import com.seatflow.payment.strategy.PaymentStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPaymentService implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentStrategyRegistry strategyRegistry;

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
            kafkaTemplate.send(
                    EventTopic.PAYMENT_COMPLETED,
                    command.userId(),
                    EventEnvelope.of(
                            EventTopic.PAYMENT_COMPLETED,
                            "payment-service",
                            new PaymentCompletedEvent(
                                    command.reservationId(),
                                    command.userId(),
                                    command.amount()
                            )
                    )
            );
            log.info("Payment completed: paymentNumber={}", payment.getPaymentNumber());
        } else {
            payment.fail();
            kafkaTemplate.send(
                    EventTopic.PAYMENT_FAILED,
                    command.userId(),
                    EventEnvelope.of(
                            EventTopic.PAYMENT_FAILED,
                            "payment-service",
                            new PaymentFailedEvent(
                                    command.reservationId(),
                                    command.userId(),
                                    command.amount()
                            )
                    )
            );
            log.info("Payment failed: paymentNumber={}", payment.getPaymentNumber());
        }

        return payment;
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