package com.seatflow.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {}