package com.seatflow.auth.dto;

public record ValidateResponse(
        String userId,
        String email
) {}