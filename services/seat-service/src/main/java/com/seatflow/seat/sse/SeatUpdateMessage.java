package com.seatflow.seat.sse;

import com.seatflow.common.event.seat.SeatStatusChangedEvent;

public record SeatUpdateMessage(Long seatId, String status) {
    public static SeatUpdateMessage from(SeatStatusChangedEvent e) {
        return new SeatUpdateMessage(e.seatId(), e.status());
    }
}