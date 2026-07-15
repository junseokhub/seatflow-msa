package com.seatflow.common.event.user;

import com.seatflow.common.event.VersionedEvent;

public record UserRegisteredEvent(
        String userId,
        String email,
        String name
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}