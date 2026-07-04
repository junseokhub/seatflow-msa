package com.seatflow.reservation.repository;

import com.seatflow.reservation.domain.CancelSaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CancelSagaRepository extends JpaRepository<CancelSaga, Long> {
    Optional<CancelSaga> findByReservationId(Long reservationId);
}
 