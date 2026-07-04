package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatReleasedEvent;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReleasedEventConsumer {

    private final CancelSagaOrchestrator orchestrator;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.SEAT_RELEASED, groupId = "reservation-service")
    public void consume(String message) {
        SeatReleasedEvent payload;
        try {
            EventEnvelope<SeatReleasedEvent> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<SeatReleasedEvent>>() {});
            payload = event.payload();
        } catch (Exception e) {
            log.error("Malformed seat.released skipped: {}", e.getMessage(), e);
            return;
        }
        orchestrator.onSeatReleased(payload.sagaId(), payload.reservationId());
    }
}