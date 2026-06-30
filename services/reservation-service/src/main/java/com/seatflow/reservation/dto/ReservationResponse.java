package com.seatflow.reservation.dto;

import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        String reservationNumber,
        String userId,
        String showId,
        Long seatId,
        BigDecimal amount,
        ReservationStatus status,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getReservationNumber(),
                reservation.getUserId(),
                reservation.getShowId(),
                reservation.getSeatId(),
                reservation.getAmount(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }
}