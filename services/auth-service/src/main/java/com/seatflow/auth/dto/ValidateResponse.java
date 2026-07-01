package com.seatflow.auth.dto;

import com.seatflow.common.security.Role;

public record ValidateResponse(
        String userId,
        String email,
        Role role
) {}