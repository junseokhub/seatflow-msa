package com.seatflow.reservation.service.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateReservationCommand(
        String userId,
        String showId,
        Long seatId,
        BigDecimal amount,
        LocalDateTime showDate
) {}