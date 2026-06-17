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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime publishingAt;

    @Column
    private LocalDateTime publishedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
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

    public void markPendingWithRetry() {
        this.status = OutboxStatus.PENDING;
        this.retryCount++;
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    public void markPending() {
        this.status = OutboxStatus.PENDING;
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