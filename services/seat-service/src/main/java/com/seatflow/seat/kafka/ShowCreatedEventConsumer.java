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

// ShowCreatedEventConsumer.consume() 수정. JSON 문법은 맞지만 필수 필드(showDate,
// grades)가 비어있는 "의미적으로 불완전한" 메시지가 malformed 체크를 통과해버리고
// SeatGenerationService까지 넘어가 NPE를 내는 문제를 발견했다 — 테스트로 필수
// 필드 없는 메시지를 넣어보고서야 드러났다.

    @KafkaListener(topics = EventTopic.SHOW_CREATED, groupId = GROUP)
    public void consume(String message) {
        EventEnvelope<ShowCreatedEvent> event;
        try {
            event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<ShowCreatedEvent>>() {
                    });
        } catch (Exception e) {
            log.error("Malformed show.created: {}", e.getMessage());
            throw new IllegalStateException("Malformed show.created", e);
        }

        ShowCreatedEvent payload = event.payload();
        if (payload == null || payload.showId() == null || payload.showDate() == null
                || payload.grades() == null || payload.grades().isEmpty()) {
            log.error("Incomplete show.created payload, skip: {}", payload);
            throw new IllegalStateException("Incomplete show.created payload: " + payload);
        }

        seatGenerationService.createSeats(payload);
    }
}