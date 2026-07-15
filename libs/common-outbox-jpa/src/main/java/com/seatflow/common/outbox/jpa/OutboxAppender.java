package com.seatflow.common.outbox.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.VersionedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 적재 공통 헬퍼. 각 서비스가 복붙하던 EventEnvelope 래핑 + JSON 직렬화 + save를 한곳으로 모은다.
 * 서비스는 append 한 줄만 호출하면 된다.
 *
 * 직렬화는 kafkaObjectMapper(common-kafka)를 재사용해 발행 시 직렬화와 포맷을 맞춘다.
 * 빈 등록은 CommonOutboxAutoConfiguration의 @Bean으로 한다(컴포넌트 스캔 아님).
 *
 * event 파라미터를 Object가 아니라 VersionedEvent로 명시했다.
 * Outbox로 나가는 이벤트는 반드시 자신의 스키마 버전(eventVersion())을 제공해야 한다는 걸 컴파일 타임에 강제하기 위함이다.
 * 이 인터페이스를 구현 안 한 이벤트 record를 append()에 넘기면 컴파일 자체가 안 된다.
 * 런타임 캐스팅 실패보다 훨씬 안전하다.
 */
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper kafkaObjectMapper;

    /**
     * 이벤트를 Outbox에 적재한다. 호출자의 트랜잭션에 참여(REQUIRED)해,
     * 비즈니스 저장과 이벤트 적재가 원자적으로 함께 커밋되게 한다(dual-write 제거의 핵심).
     *
     * @param eventType  토픽이자 이벤트 타입 (예: EventTopic.SEAT_HELD)
     * @param source     발행 서비스명 (예: seat-service)
     * @param messageKey Kafka 파티션 키이자 EventEnvelope의 aggregateId
     *                   (예: userId, reservationId  이 이벤트가 어떤 대상에 대한 것인지를 나타내는 값이라 의미가 동일하다)
     * @param event      페이로드 객체. 반드시 VersionedEvent를 구현해야 한다.
     */
    @Transactional
    public void append(String eventType, String source, String messageKey, VersionedEvent event) {
        EventEnvelope<?> envelope = EventEnvelope.of(
                eventType, event.eventVersion(), source, messageKey, event);
        outboxRepository.save(Outbox.builder()
                .eventId(envelope.eventId())
                .eventType(eventType)
                .messageKey(messageKey)
                .payload(serialize(envelope))
                .build());
    }

    private String serialize(Object o) {
        try {
            return kafkaObjectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new OutboxSerializationException(e);
        }
    }
}