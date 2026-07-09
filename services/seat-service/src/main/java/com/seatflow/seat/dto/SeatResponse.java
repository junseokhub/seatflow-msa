package com.seatflow.seat.dto;

import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;

public record SeatResponse(
        Long id,
        String showId,
        String section,
        String seatRow,
        int number,
        SeatStatus status,
        int price,
        Integer posX,
        Integer posY
) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getShowId(),
                seat.getSection(),
                seat.getSeatRow(),
                seat.getNumber(),
                seat.getStatus(),
                seat.getPrice(),
                seat.getPosX(),
                seat.getPosY()
        );
    }
}
