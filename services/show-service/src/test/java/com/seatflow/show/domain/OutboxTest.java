package com.seatflow.show.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox는 상태 전이 메서드가 없는 순수 데이터 구조다 — 상태 전이는
 * MongoOutboxStore가 findAndModify로 원자적으로 수행하므로(더티체킹 방식이아님),
 * 이 도메인 자체가 검증할 로직은 "생성자가 초기값을 정확히 채우는가"뿐이다.
 * 실제 상태 전이(PENDING→PUBLISHING→PUBLISHED/FAILED)는 MongoOutboxStoreIntegrationTest에서 이미 검증했다.
 */
class OutboxTest {

    @Test
    @DisplayName("생성 직후 status는 PENDING, retryCount는 0으로 초기화된다")
    void initializesWithPendingStatusAndZeroRetryCount() {
        Outbox outbox = Outbox.builder()
                .eventId("event-1")
                .eventType("show.created")
                .messageKey("show-1")
                .payload("{}")
                .build();

        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("생성 직후 createdAt과 nextRetryAt이 채워지고, 둘은 거의 동시각이다")
    void createdAtAndNextRetryAtAreSetToNearlyTheSameTime() {
        LocalDateTime before = LocalDateTime.now();

        Outbox outbox = Outbox.builder()
                .eventId("event-1")
                .eventType("show.created")
                .messageKey("show-1")
                .payload("{}")
                .build();

        LocalDateTime after = LocalDateTime.now();

        assertThat(outbox.getCreatedAt()).isBetween(before, after);
        assertThat(outbox.getNextRetryAt()).isBetween(before, after);
        // 생성 즉시 발행 대상이어야 하므로, nextRetryAt이 미래로 미뤄져 있으면 안 된다.
        assertThat(outbox.getNextRetryAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("publishingAt, publishedAt은 생성 시점엔 아직 null이다 (발행 전이므로)")
    void publishingAndPublishedTimestampsAreNullInitially() {
        Outbox outbox = Outbox.builder()
                .eventId("event-1")
                .eventType("show.created")
                .messageKey("show-1")
                .payload("{}")
                .build();

        assertThat(outbox.getPublishingAt()).isNull();
        assertThat(outbox.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("빌더로 넘긴 eventId, eventType, messageKey, payload가 그대로 반영된다")
    void builderFieldsAreSetCorrectly() {
        Outbox outbox = Outbox.builder()
                .eventId("event-42")
                .eventType("payment.completed")
                .messageKey("reservation-100")
                .payload("{\"key\":\"value\"}")
                .build();

        assertThat(outbox.getEventId()).isEqualTo("event-42");
        assertThat(outbox.getEventType()).isEqualTo("payment.completed");
        assertThat(outbox.getMessageKey()).isEqualTo("reservation-100");
        assertThat(outbox.getPayload()).isEqualTo("{\"key\":\"value\"}");
    }
}