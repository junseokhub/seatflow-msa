package com.seatflow.payment.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentRefundCommand;
import com.seatflow.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 취소 Saga의 환불 명령을 받아 처리한다. 결과(성공/실패)에 따라
 * payment.refunded 또는 payment.refund.failed로 응답한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundCommandConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.PAYMENT_REFUND_COMMAND, groupId = "payment-service")
    public void consume(String message) {
        PaymentRefundCommand command;
        try {
            EventEnvelope<PaymentRefundCommand> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentRefundCommand>>() {});
            command = event.payload();
        } catch (Exception e) {
            log.error("Malformed payment.refund.command: {}", e.getMessage());
            throw new IllegalStateException("Malformed payment.refund.command", e);
        }

        paymentService.executeRefund(command.sagaId(), command.reservationId(), command.refundAmount());
    }
}