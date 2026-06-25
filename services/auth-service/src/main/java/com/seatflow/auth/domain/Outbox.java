package com.seatflow.auth.domain;

import com.seatflow.common.outbox.OutboxMessage;
import com.seatflow.common.outbox.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA 구현의 Outbox 엔티티(어댑터). common-outbox에서 auth-service로 이동.
 * OutboxMessage를 구현해 공통 스케줄러가 이 엔티티를 인터페이스로만 다루게 한다.
 * PK는 Long이지만 getId()는 String으로 노출(인터페이스 계약).
 */
@Entity
@Table(name = "outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Outbox implements OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter(AccessLevel.NONE)   // lombok이 getId():Long 만들지 않게 — OutboxMessage.getId():String과 충돌 방지
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String eventId;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private String messageKey;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime publishingAt;

    @Column
    private LocalDateTime publishedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.nextRetryAt = now;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public void markPublishing() {
        this.status = OutboxStatus.PUBLISHING;
        this.publishingAt = LocalDateTime.now();
    }

    public void markPublished() {
        if (this.status != OutboxStatus.PUBLISHING) return;
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markRetry(LocalDateTime nextRetryAt) {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
    }

    public void markPendingNow() {
        this.status = OutboxStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now();
    }

    public void markRedrive() {
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    public boolean isExceededRetry(int maxRetry) {
        return this.retryCount >= maxRetry;
    }

    @Builder
    private Outbox(String eventId, String eventType, String messageKey, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.messageKey = messageKey;
        this.payload = payload;
    }

    // === OutboxMessage 구현 ===
    // getEventId/getEventType/getMessageKey/getPayload는 @Getter가 생성 → 그대로 충족
    // getId만 Long → String 변환 필요
    @Override
    public String getId() {
        return String.valueOf(this.id);
    }
}