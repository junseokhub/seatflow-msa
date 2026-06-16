package com.seatflow.reservation.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.exception.ReservationErrorCode;
import com.seatflow.reservation.repository.ReservationRepository;
import com.seatflow.reservation.service.command.CreateReservationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;

    @Transactional
    public Reservation createReservation(CreateReservationCommand command) {
        Reservation reservation = Reservation.builder()
                .userId(command.userId())
                .showId(command.showId())
                .seatId(command.seatId())
                .build();

        return reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ReservationErrorCode.RESERVATION_NOT_FOUND.getStatus().value(),
                        ReservationErrorCode.RESERVATION_NOT_FOUND.getMessage()
                ));
    }

    @Transactional(readOnly = true)
    public List<Reservation> getUserReservations(String userId) {
        return reservationRepository.findByUserId(userId);
    }

    @Transactional
    public void cancelReservation(Long id) {
        Reservation reservation = getReservation(id);

        if (reservation.getStatus() == com.seatflow.reservation.domain.ReservationStatus.CANCELLED) {
            throw new BusinessException(
                    ReservationErrorCode.RESERVATION_ALREADY_CANCELLED.getStatus().value(),
                    ReservationErrorCode.RESERVATION_ALREADY_CANCELLED.getMessage()
            );
        }

        reservation.cancel();
    }
}