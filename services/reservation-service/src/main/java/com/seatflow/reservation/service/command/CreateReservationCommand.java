package com.seatflow.reservation.service.command;

import java.math.BigDecimal;

public record CreateReservationCommand(
        String userId,
        String showId,
        Long seatId,
        BigDecimal amount
) {}