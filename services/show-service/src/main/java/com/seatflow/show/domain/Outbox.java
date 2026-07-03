package com.seatflow.show.domain;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Mongo 전용 Outbox 도큐먼트. show-service 안에서 단독으로 사용하며,
 * 공통 outbox 모듈(interface)에 의존하지 않는다.
 * 상태 전이는 MongoOutboxStore에서 findAndModify로 원자적으로 수행하므로
 * (JPA처럼 더티체킹으로 자동 반영되지 않음) 여기서는 도큐먼트 구조와 정적 팩토리만 둔다.
 */
@Document(collection = "outbox")
@Getter
public class Outbox {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    private String eventType;

    private String messageKey;

    private String payload;

    private OutboxStatus status;

    private int retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime publishingAt;

    private LocalDateTime publishedAt;

    @Builder
    private Outbox(String eventId, String eventType, String messageKey, String payload) {
        LocalDateTime now = LocalDateTime.now();
        this.eventId = eventId;
        this.eventType = eventType;
        this.messageKey = messageKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;   // 생성 즉시 발행 대상
        this.retryCount = 0;
        this.nextRetryAt = now;
        this.createdAt = now;
    }
}