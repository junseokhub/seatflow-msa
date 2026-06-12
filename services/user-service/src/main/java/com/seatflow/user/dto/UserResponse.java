package com.seatflow.user.dto;

import com.seatflow.user.domain.User;

import java.time.LocalDateTime;

public record UserResponse(
        String id,
        String email,
        String name,
        String phone,
        String status,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}