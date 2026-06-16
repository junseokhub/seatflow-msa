package com.seatflow.show.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @NotNull LocalDateTime showDate,
        @Positive int totalSeats,
        @NotNull BigDecimal price
) {}