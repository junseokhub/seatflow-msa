package com.seatflow.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 모든 서비스가 Outbox로 발행하는 이벤트를 감싸는 공통 봉투.
 *
 * 변경 이력(기존 구조에서):
 *   - version → eventVersion으로 이름 변경 (EventEnvelope 자체의 버전이 아니라 payload 스키마의 버전이라는 의미를 명확히 하기 위함)
 *   - occurredAt: LocalDateTime → Instant (서버마다 타임존이 다를 수 있는 MSA 환경에서, "언제 발생했는지"를 항상 절대 시각(UTC)으로 고정하기 위함)
 *   - aggregateId 필드 신규 추가
 *   ("이 이벤트가 어떤 대상에 대한 것인지"를 payload 안에서 각자 파싱하지 않고 봉투 레벨에서 바로 알 수 있게 함 Kafka 파티션 키와 동일한 값을 쓴다)
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String eventVersion,
        String source,
        Instant occurredAt,
        String aggregateId,
        T payload
) {
    public static <T> EventEnvelope<T> of(
            String eventType, String eventVersion, String source, String aggregateId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                eventVersion,
                source,
                Instant.now(),
                aggregateId,
                payload
        );
    }
}