package com.seatflow.common.event.user;

public record UserCreatedEvent(
        Long userId,
        String email,
        String name
) {}