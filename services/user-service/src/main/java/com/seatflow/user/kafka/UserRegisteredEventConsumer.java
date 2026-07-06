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
    //    private final IdempotentEventProcessor idempotentProcessor;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.USER_REGISTERED, groupId = GROUP)
    public void consume(String message) {
        EventEnvelope<UserRegisteredEvent> event;
        try {
            event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<UserRegisteredEvent>>() {
                    });
        } catch (Exception e) {
            log.error("Malformed user.registered: {}", e.getMessage());
            throw new IllegalStateException("Malformed user.registered", e);
//            log.error("Malformed event skipped: {}", e.getMessage(), e);
//            return;   // 깨진 메시지(poison) → 스킵
        }

        // 멱등 처리는 createUser(REQUIRES_NEW + 충돌 무시)가 담당. 컨슈머는 위임만 한다.
        UserRegisteredEvent payload = event.payload();
        userService.createUser(payload.userId(), payload.email(), payload.name());

// IGNORE
//        // 멱등 처리는 UserService.createUser(INSERT IGNORE)가 담당.
//        // 중복 이벤트는 예외 없이 무시되므로 별도 try-catch가 필요 없다.
//        UserRegisteredEvent payload = event.payload();
//        userService.createUser(payload.userId(), payload.email(), payload.name());

// save + catch
//        try {
//            userService.createUser(payload.userId(), payload.email(), payload.name());
//        } catch (DataIntegrityViolationException e) {
//            // PK(userId) 또는 email unique 충돌 = 이미 처리된 중복 이벤트 → 무시
//            // (at-least-once로 같은 이벤트가 또 와도 DB 제약이 원자적으로 막아줌)
//            log.info("Duplicate event skipped (already processed): eventId={}", event.eventId());
//        }
    }
}