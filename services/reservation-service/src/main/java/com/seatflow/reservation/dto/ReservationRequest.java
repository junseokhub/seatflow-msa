package com.seatflow.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotBlank String showId,
        @NotNull Long seatId
) {}