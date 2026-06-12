package com.seatflow.user.kafka;

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

    @KafkaListener(topics = EventTopic.USER_REGISTERED, groupId = "user-service")
    public void consume(EventEnvelope<UserRegisteredEvent> event) {
        log.info("Received UserRegisteredEvent: eventId={}", event.eventId());
        userService.createUser(
                event.payload().userId(),
                event.payload().email(),
                event.payload().name()
        );
    }
}