package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentRefundFailedEvent;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundFailedEventConsumer {

    private final CancelSagaOrchestrator orchestrator;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.PAYMENT_REFUND_FAILED, groupId = "reservation-service")
    public void consume(String message) {
        PaymentRefundFailedEvent payload;
        try {
            EventEnvelope<PaymentRefundFailedEvent> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentRefundFailedEvent>>() {});
            payload = event.payload();
        } catch (Exception e) {
            log.error("Malformed payment.refund.failed: {}", e.getMessage());
            throw new IllegalStateException("Malformed payment.refund.failed", e);
        }
        orchestrator.onPaymentRefundFailed(payload.sagaId(), payload.reservationId(), payload.reason());
    }
}