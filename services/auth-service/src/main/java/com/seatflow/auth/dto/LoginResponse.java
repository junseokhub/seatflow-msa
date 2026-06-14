package com.seatflow.auth.dto;

public record LoginResponse(
        String userId,
        String email
) {}