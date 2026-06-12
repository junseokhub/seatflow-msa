package com.seatflow.common.event.user;

public record UserRegisteredEvent(
        String userId,
        String email,
        String name
) {}