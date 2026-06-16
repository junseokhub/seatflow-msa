package com.seatflow.reservation.repository;

import com.seatflow.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUserId(String userId);
    List<Reservation> findByShowId(String showId);
}