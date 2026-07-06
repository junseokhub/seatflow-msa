package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentRefundedEvent;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundedEventConsumer {

    private final CancelSagaOrchestrator orchestrator;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.PAYMENT_REFUNDED, groupId = "reservation-service")
    public void consume(String message) {
        PaymentRefundedEvent payload;
        try {
            EventEnvelope<PaymentRefundedEvent> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentRefundedEvent>>() {});
            payload = event.payload();
        } catch (Exception e) {
            log.error("Malformed payment.refunded: {}", e.getMessage());
            throw new IllegalStateException("Malformed payment.refunded", e);
        }
        orchestrator.onPaymentRefunded(payload.sagaId(), payload.reservationId());
    }
}