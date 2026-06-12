package com.seatflow.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String source,
        String version,
        LocalDateTime occurredAt,
        T payload
) {
    public static <T> EventEnvelope<T> of(String eventType, String source, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                source,
                "1.0",
                LocalDateTime.now(),
                payload
        );
    }
}