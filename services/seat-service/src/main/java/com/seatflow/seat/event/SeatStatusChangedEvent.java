package com.seatflow.seat.event;

public record SeatStatusChangedEvent(String showId, Long seatId, String status) {}
