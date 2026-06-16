package com.seatflow.common.event.seat;

public record SeatHeldEvent(
        String userId,
        String showId,
        Long seatId
) {}