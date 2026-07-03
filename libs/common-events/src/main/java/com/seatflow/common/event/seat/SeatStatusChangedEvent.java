package com.seatflow.common.event.seat;

public record SeatStatusChangedEvent(String showId, Long seatId, String status) {}
