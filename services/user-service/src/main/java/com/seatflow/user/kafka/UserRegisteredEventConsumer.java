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

/**
 * 컨슈머는 파싱과 위임만 담당한다. 멱등 처리는 전적으로 주입받은 서비스의책임이다.
 * 현재 활성은 UserService.createUser()([4] noRollbackFor)이고,
 * 그 외 3가지 방식으로 바꾸려면 필드와 아래 위임 코드를 다음과 같이 교체하면 된다.
 *
 * ───────────────────────────────────────────────────────────────
 * [4] noRollbackFor (현재 활성)
 *
 *     private final UserService userService;
 *     ...
 *     userService.createUser(payload.userId(), payload.email(), payload.name());
 *
 *     -> 별도 처리 불필요. createUser 내부에서 예외를 잡아 조용히 삼키므로 컨슈머는 그 결과를 신경 쓸 필요가 없다.
 *
 * ───────────────────────────────────────────────────────────────
 * [1] save + catch
 *
 *     private final UserServiceSaveAndCatch userService;
 *     ...
 *     userService.createUser(payload.userId(), payload.email(), payload.name());
 *
 *     -> UserServiceSaveAndCatch.createUser() 내부에서 이미 예외를 잡으므로,
 *       호출부(컨슈머)는 [4]와 동일하게 그대로 호출하면 된다.
 *       다만 이 방식은 컨슈머에 별도 @Transactional이 걸려 있지 않다는 전제에서만 안전하다/
 *       이 컨슈머 메서드 자체에 트랜잭션이 없으므로 지금은 조건이 충족된다.
 *
 * ───────────────────────────────────────────────────────────────
 * [2] INSERT IGNORE
 *
 *     private final UserServiceInsertIgnore userService;
 *     ...
 *     userService.createUser(payload.userId(), payload.email(), payload.name());
 *
 *     -> 이것도 호출부는 동일하다. 예외 자체가 나지 않는 방식이라 try-catch가 원천적으로 필요 없다.
 *
 * ───────────────────────────────────────────────────────────────
 * [3] REQUIRES_NEW
 *
 *     private final UserServiceRequiresNew userService;
 *     ...
 *     userService.createUser(payload.userId(), payload.email(), payload.name());
 *
 *     -> 호출부는 동일하다. 다만 매 호출마다 새 트랜잭션을 여는 비용이 있다는 차이는 호출부 코드에는 드러나지 않는다(서비스 내부 구현 차이).
 *
 * ───────────────────────────────────────────────────────────────
 * [Inbox] IdempotentEventProcessor 기반
 *
 *     private final UserServiceInboxPattern userService;
 *     ...
 *     // 이 방식만 유일하게 eventId를 함께 넘겨야 한다.
 *     // 처리 이력을  (consumerGroup, eventId)로 추적하기 때문이다. EventEnvelope가 이미 eventId를 갖고 있으므로 그대로 꺼내 넘기면 된다.
 *     userService.createUser(event.eventId(), payload.userId(), payload.email(), payload.name());
 *
 *     -> 다른 세 방식과 메서드 시그니처 자체가 다르다(eventId 파라미터 추가)는 점이 갈아끼울 때 유일하게 호출부 수정이 필요한 지점이다.
 * ───────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

    private static final String GROUP = "user-service";

    private final UserService userService;
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
        }

        UserRegisteredEvent payload = event.payload();
        userService.createUser(payload.userId(), payload.email(), payload.name());
    }
}