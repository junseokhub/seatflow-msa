package com.seatflow.coupon.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCampaignRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal discountAmount,
        @NotNull @Positive Integer totalQuantity,
        LocalDateTime expiresAt   // null이면 무기한
) {}