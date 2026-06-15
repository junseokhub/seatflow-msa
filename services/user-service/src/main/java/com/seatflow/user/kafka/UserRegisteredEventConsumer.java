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

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTopic.USER_REGISTERED, groupId = "user-service")
    public void consume(String message) {
        try {
            EventEnvelope<UserRegisteredEvent> event = objectMapper.readValue(
                    message,
                    new TypeReference<EventEnvelope<UserRegisteredEvent>>() {}
            );
            log.info("Received UserRegisteredEvent: eventId={}, userId={}",
                    event.eventId(), event.payload().userId());
            userService.createUser(
                    event.payload().userId(),
                    event.payload().email(),
                    event.payload().name()
            );
        } catch (Exception e) {
            log.error("Failed to process UserRegisteredEvent: {}", e.getMessage(), e);
        }
    }
}