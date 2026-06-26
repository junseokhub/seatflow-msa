package com.seatflow.seat.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.show.ShowCreatedEvent;
import com.seatflow.seat.service.SeatGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShowCreatedEventConsumer {

    private static final String GROUP = "seat-service";

    private final SeatGenerationService seatGenerationService;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.SHOW_CREATED, groupId = GROUP)
    public void consume(String message) {
        EventEnvelope<ShowCreatedEvent> event;
        try {
            event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<ShowCreatedEvent>>() {
                    });
        } catch (Exception e) {
            log.error("Malformed event skipped: {}", e.getMessage(), e);
            return;   // 깨진 메시지(poison) → 스킵
        }

        seatGenerationService.createSeats(event.payload());
    }
}