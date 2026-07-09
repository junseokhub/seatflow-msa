package com.seatflow.reservation.service;

import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentRefundCommand;
import com.seatflow.common.event.seat.SeatReleaseCommand;
import com.seatflow.common.event.seat.SeatReserveCompensationCommand;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.reservation.domain.CancelSaga;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import com.seatflow.reservation.exception.ReservationErrorCode;
import com.seatflow.reservation.repository.CancelSagaRepository;
import com.seatflow.reservation.repository.ReservationRepository;
import com.seatflow.reservation.saga.CancellationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 취소 Saga 오케스트레이터. reservation이 중심이 되어 좌석 반환 → 환불을 순서대로 지휘하고,
 * 환불이 실패하면 좌석 반환을 되돌리는 보상을 지휘한다.
 *
 * 각 단계는 명령을 보내고 끝난다(비동기). 다음 단계는 응답 이벤트(컨슈머)가 왔을 때
 * 이 클래스의 다른 메서드가 이어받는다. CancelSaga가 "지금 어디까지 됐는지"를 들고 있다.
 *
 * 1차 구현 범위: 정상 경로 + 실패 시 보상까지만 다룬다. 응답이 영영 안 오는 경우(타임아웃)에
 * 대한 재시도·강제 실패 처리는 별도 스케줄러로 추후 보강한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelSagaOrchestrator {

    private static final String SOURCE = "reservation-service";

    private final ReservationRepository reservationRepository;
    private final CancelSagaRepository cancelSagaRepository;
    private final OutboxAppender outboxAppender;

    /**
     * 취소 진입점. PENDING/CONFIRMED 모두 여기서 받아 분기한다.
     *
     * PENDING  → 결제 없음. Saga 없이 즉시 CANCELLED + 좌석 반환 명령만 발행(동기 완료).
     * CONFIRMED → 기존 Saga 흐름. 좌석 반환 → 환불 → 완료(비동기).
     *
     * 멱등성(CONFIRMED): CancelSaga.reservationId unique 제약이 중복 요청을 막는다.
     */
    @Transactional
    public void startCancellation(Long reservationId, String userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(
                        ReservationErrorCode.RESERVATION_NOT_FOUND.getStatus().value(),
                        ReservationErrorCode.RESERVATION_NOT_FOUND.getMessage()));

        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(
                    ReservationErrorCode.RESERVATION_NOT_OWNED.getStatus().value(),
                    ReservationErrorCode.RESERVATION_NOT_OWNED.getMessage());
        }

        if (reservation.getStatus() == ReservationStatus.PENDING) {
            cancelPendingReservation(reservation);
            return;
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(
                    ReservationErrorCode.CANCELLATION_ALREADY_IN_PROGRESS.getStatus().value(),
                    ReservationErrorCode.CANCELLATION_ALREADY_IN_PROGRESS.getMessage());
        }

        if (cancelSagaRepository.findByReservationId(reservationId).isPresent()) {
            throw new BusinessException(
                    ReservationErrorCode.CANCELLATION_ALREADY_IN_PROGRESS.getStatus().value(),
                    ReservationErrorCode.CANCELLATION_ALREADY_IN_PROGRESS.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        if (!CancellationPolicy.isCancellable(reservation.getShowDate(), now)) {
            throw new BusinessException(
                    ReservationErrorCode.CANCELLATION_DEADLINE_PASSED.getStatus().value(),
                    ReservationErrorCode.CANCELLATION_DEADLINE_PASSED.getMessage());
        }
        BigDecimal refundAmount = CancellationPolicy.calculateRefund(
                reservation.getAmount(), reservation.getShowDate(), now);

        reservation.startCancelling();

        CancelSaga saga = cancelSagaRepository.save(CancelSaga.builder()
                .reservationId(reservationId)
                .userId(userId)
                .seatId(reservation.getSeatId())
                .showId(reservation.getShowId())
                .refundAmount(refundAmount)
                .build());

        outboxAppender.append(EventTopic.SEAT_RELEASE_COMMAND, SOURCE, String.valueOf(saga.getSeatId()),
                new SeatReleaseCommand(saga.getId(), reservationId, saga.getShowId(), saga.getSeatId()));

        log.info("Cancel saga started: sagaId={}, reservationId={}, refundAmount={}",
                saga.getId(), reservationId, refundAmount);
    }

    /**
     * 결제 전 취소. 환불 없음 → Saga 없이 즉시 CANCELLED + 좌석 반환 명령 발행.
     * sagaId=null이므로 seat.released가 돌아와도 오케스트레이터는 경고 로그만 남기고 스킵한다.
     */
    private void cancelPendingReservation(Reservation reservation) {
        reservation.cancelPending();

        outboxAppender.append(EventTopic.SEAT_RELEASE_COMMAND, SOURCE,
                String.valueOf(reservation.getSeatId()),
                new SeatReleaseCommand(null, reservation.getId(), reservation.getShowId(), reservation.getSeatId()));

        log.info("Pending reservation cancelled directly (no payment to refund): reservationId={}",
                reservation.getId());
    }

    /**
     * 좌석 반환 완료 응답 처리(seat.released 컨슈머가 호출).
     * 다음 단계인 환불 명령을 발행한다.
     */
    @Transactional
    public void onSeatReleased(Long sagaId, Long reservationId) {
        CancelSaga saga = cancelSagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("CancelSaga not found for seat.released, skip: sagaId={}", sagaId);
            return;
        }
        if (!saga.isStarted()) {
            log.info("Cancel saga already progressed past STARTED, skip duplicate seat.released: sagaId={}, status={}",
                    sagaId, saga.getStatus());
            return;   // 중복 응답(at-least-once) 방어. 이미 다음 단계로 갔으면 무시.
        }

        saga.markSeatReleased();

        outboxAppender.append(EventTopic.PAYMENT_REFUND_COMMAND, SOURCE, String.valueOf(reservationId),
                new PaymentRefundCommand(sagaId, reservationId, saga.getRefundAmount()));

        log.info("Cancel saga: seat released, refund command sent: sagaId={}", sagaId);
    }


    // CancelSagaOrchestrator.onPaymentRefunded — 원본(12편)으로 복구.
    // 쿠폰 복원은 payment-service의 executeRefund가 이미 처리했으므로,
    // reservation은 couponClient를 가질 필요가 없다. 파라미터도 2개(sagaId, reservationId) 그대로.

    /**
     * 환불 완료 응답 처리(payment.refunded 컨슈머가 호출). Saga 최종 완료.
     */
    @Transactional
    public void onPaymentRefunded(Long sagaId, Long reservationId) {
        CancelSaga saga = cancelSagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("CancelSaga not found for payment.refunded, skip: sagaId={}", sagaId);
            return;
        }
        if (!saga.isSeatReleased()) {
            log.info("Cancel saga not in SEAT_RELEASED, skip duplicate payment.refunded: sagaId={}, status={}",
                    sagaId, saga.getStatus());
            return;
        }

        saga.markRefunded();
        saga.markCompleted();

        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation != null) {
            reservation.cancel();   // CANCELLING → CANCELLED
        }

        log.info("Cancel saga completed: sagaId={}, reservationId={}", sagaId, reservationId);
    }


    /**
     * 환불 실패 응답 처리(payment.refund.failed 컨슈머가 호출).
     * 보상 시작: 반환했던 좌석을 다시 점유시키는 명령을 발행한다.
     */
    @Transactional
    public void onPaymentRefundFailed(Long sagaId, Long reservationId, String reason) {
        CancelSaga saga = cancelSagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("CancelSaga not found for payment.refund.failed, skip: sagaId={}", sagaId);
            return;
        }
        if (!saga.isSeatReleased()) {
            log.info("Cancel saga not in SEAT_RELEASED, skip duplicate refund.failed: sagaId={}, status={}",
                    sagaId, saga.getStatus());
            return;
        }

        log.warn("Payment refund failed, starting compensation: sagaId={}, reason={}", sagaId, reason);

        saga.markCompensating();

        outboxAppender.append(EventTopic.SEAT_RESERVE_COMPENSATION_COMMAND, SOURCE,
                String.valueOf(saga.getSeatId()),
                new SeatReserveCompensationCommand(sagaId, reservationId, saga.getShowId(), saga.getSeatId()));
    }

    /**
     * 보상(좌석 재점유) 완료 응답 처리(seat.reserved.compensated 컨슈머가 호출).
     * 취소 자체는 실패로 마무리하고, 예매를 원상복구(CANCELLING → CONFIRMED)한다.
     */
    @Transactional
    public void onSeatReservedCompensated(Long sagaId, Long reservationId) {
        CancelSaga saga = cancelSagaRepository.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("CancelSaga not found for compensation reply, skip: sagaId={}", sagaId);
            return;
        }
        if (!saga.isCompensating()) {
            log.info("Cancel saga not in COMPENSATING, skip duplicate compensation reply: sagaId={}, status={}",
                    sagaId, saga.getStatus());
            return;
        }

        saga.markFailed();

        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation != null) {
            reservation.revertCancelling();   // CANCELLING → CONFIRMED (원상복구)
        }

        log.info("Cancel saga failed and compensated (reservation restored): sagaId={}, reservationId={}",
                sagaId, reservationId);
    }
}