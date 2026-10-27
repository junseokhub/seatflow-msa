package com.seatflow.reservation.repository;

import com.seatflow.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUserId(String userId);
    Optional<Reservation> findByIdAndUserId(Long id, String userId);
    List<Reservation> findByShowId(String showId);
}