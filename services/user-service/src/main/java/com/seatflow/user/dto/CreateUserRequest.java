package com.seatflow.user.dto;

public record CreateUserRequest(
        String email,
        String name,
        String phone
) {}