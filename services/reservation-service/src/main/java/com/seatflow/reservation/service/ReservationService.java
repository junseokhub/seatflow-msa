package com.seatflow.reservation.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
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

    /**
     * 결제 완료로 예매를 확정한다(payment.completed 컨슈머가 호출).
     * 존재하지 않는 예매면 로깅 후 무시한다(이벤트 순서/지연으로 아직 없을 수 있음).
     * 상태 전이 멱등성은 Reservation.confirm()이 보장한다.
     */
    @Transactional
    public void confirmReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElse(null);
        if (reservation == null) {
            log.warn("Reservation not found for confirm, skip: reservationId={}", reservationId);
            return;
        }
        reservation.confirm();   // PENDING → CONFIRMED (이미 CONFIRMED면 멱등 무시)
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

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException(
                    ReservationErrorCode.RESERVATION_ALREADY_CANCELLED.getStatus().value(),
                    ReservationErrorCode.RESERVATION_ALREADY_CANCELLED.getMessage()
            );
        }

        reservation.cancel();
    }
}