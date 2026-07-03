package com.seatflow.common.outbox.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OutboxStore의 JPA 구현이 쓰는 엔티티. 서비스마다 자기 DB에 동일한 스키마(outbox 테이블)를
 * 두고 이 엔티티를 그대로 매핑한다 (엔티티만 공유, 데이터는 서비스별로 완전히 분리됨).
 */
@Entity
@Table(name = "outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}