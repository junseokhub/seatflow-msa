package com.seatflow.reservation.service.command;

public record CreateReservationCommand(
        String userId,
        String showId,
        Long seatId
) {}