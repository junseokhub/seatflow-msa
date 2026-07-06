package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatReservedCompensatedEvent;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReservedCompensatedEventConsumer {

    private final CancelSagaOrchestrator orchestrator;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.SEAT_RESERVED_COMPENSATED, groupId = "reservation-service")
    public void consume(String message) {
        SeatReservedCompensatedEvent payload;
        try {
            EventEnvelope<SeatReservedCompensatedEvent> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<SeatReservedCompensatedEvent>>() {});
            payload = event.payload();
        } catch (Exception e) {
            log.error("Malformed seat.reserved.compensated: {}", e.getMessage());
            throw new IllegalStateException("Malformed seat.reserved.compensated", e);
        }
        orchestrator.onSeatReservedCompensated(payload.sagaId(), payload.reservationId());
    }
}