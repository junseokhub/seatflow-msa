package com.seatflow.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent(String consumerGroup, String eventId, String eventType) {
        this.id = new ProcessedEventId(consumerGroup, eventId);
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now();
    }
}