package com.seatflow.common.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
        this.nextRetryAt = now;        // 생성 즉시 발행 대상
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public void markPublishing() {
        this.status = OutboxStatus.PUBLISHING;
        this.publishingAt = LocalDateTime.now();
    }

    public void markPublished() {
        if (this.status != OutboxStatus.PUBLISHING) return; // 상태 전이 가드
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /** 발행 실패 → 백오프된 시각에 재시도 */
    public void markRetry(LocalDateTime nextRetryAt) {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
    }

    /** PUBLISHING 스턱 복구 → 즉시 재시도 대상으로 */
    public void markPendingNow() {
        this.status = OutboxStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now();
    }

    /** FAILED 격리 → 운영자가 수동 재투입(redrive) */
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