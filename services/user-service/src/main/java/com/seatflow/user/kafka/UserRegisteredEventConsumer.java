package com.seatflow.user.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.user.UserRegisteredEvent;
import com.seatflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

    private static final String GROUP = "user-service";

    private final UserService userService;
    private final IdempotentEventProcessor idempotentProcessor;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.USER_REGISTERED, groupId = GROUP)
    public void consume(String message) {
        EventEnvelope<UserRegisteredEvent> event;
        try {
            event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<UserRegisteredEvent>>() {});
        } catch (Exception e) {
            // 역직렬화 불가 = 재시도해도 영원히 실패하는 poison → 일단 스킵 (추후 DLQ)
            log.error("Malformed event skipped: {}", e.getMessage(), e);
            return;
        }

        UserRegisteredEvent payload = event.payload();
        idempotentProcessor.process(GROUP, event.eventId(), event.eventType(),
                () -> userService.createUser(payload.userId(), payload.email(), payload.name()));
    }
}