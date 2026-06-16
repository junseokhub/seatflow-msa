package com.seatflow.seat.repository;

import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowId(String showId);
    List<Seat> findByShowIdAndStatus(String showId, SeatStatus status);
}