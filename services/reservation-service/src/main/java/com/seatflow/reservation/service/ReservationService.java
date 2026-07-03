package com.seatflow.reservation.service;

import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.reservation.ReservationConfirmedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
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

    private static final String SOURCE = "reservation-service";
    private final OutboxAppender outboxAppender;
    private final ReservationRepository reservationRepository;

    @Transactional
    public Reservation createReservation(CreateReservationCommand command) {
        Reservation reservation = Reservation.builder()
                .userId(command.userId())
                .showId(command.showId())
                .seatId(command.seatId())
                .amount(command.amount())
                .showDate(command.showDate())
                .build();

        return reservationRepository.save(reservation);
    }

    /**
     * 결제 완료로 예매를 확정한다(payment.completed 컨슈머가 호출).
     * 확정에 성공하면(PENDING → CONFIRMED) reservation.confirmed를 Outbox에 적재해
     * seat이 좌석을 RESERVED로 확정하게 한다. 이미 CONFIRMED였다면(멱등 무시) 재발행하지 않는다.
     */
    @Transactional
    public void confirmReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElse(null);
        if (reservation == null) {
            log.warn("Reservation not found for confirm, skip: reservationId={}", reservationId);
            return;
        }

        boolean wasPending = reservation.getStatus() == ReservationStatus.PENDING;
        reservation.confirm();   // PENDING → CONFIRMED (이미 CONFIRMED면 멱등 무시)

        // 실제로 이번에 확정된 경우에만 발행(중복 payment.completed로 인한 중복 발행 방지)
        if (wasPending) {
            outboxAppender.append(EventTopic.RESERVATION_CONFIRMED, SOURCE,
                    String.valueOf(reservation.getSeatId()),
                    new ReservationConfirmedEvent(
                            reservation.getId(),
                            reservation.getUserId(),
                            reservation.getShowId(),
                            reservation.getSeatId()));
        }
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