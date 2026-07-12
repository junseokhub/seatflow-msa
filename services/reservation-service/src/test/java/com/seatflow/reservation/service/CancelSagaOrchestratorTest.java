package com.seatflow.reservation.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.reservation.domain.CancelSaga;
import com.seatflow.reservation.domain.CancelSagaStatus;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import com.seatflow.reservation.exception.ReservationErrorCode;
import com.seatflow.reservation.repository.CancelSagaRepository;
import com.seatflow.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelSagaOrchestratorTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private CancelSagaRepository cancelSagaRepository;
    @Mock
    private OutboxAppender outboxAppender;

    private CancelSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new CancelSagaOrchestrator(reservationRepository, cancelSagaRepository, outboxAppender);
    }

    private Reservation reservationWithStatus(ReservationStatus status, LocalDateTime showDate) {
        return Reservation.builder()
                .userId("user1").showId("show-1").seatId(1L)
                .amount(BigDecimal.valueOf(10000)).showDate(showDate)
                .status(status)
                .build();
    }

    private CancelSaga sagaWithStatus(CancelSagaStatus status) {
        return CancelSaga.builder()
                .reservationId(100L).userId("user1").seatId(1L).showId("show-1")
                .refundAmount(BigDecimal.valueOf(9000))
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("startCancellation()")
    class StartCancellation {

        @Test
        @DisplayName("PENDING 예매는 Saga 없이 즉시 취소되고 좌석 반환 명령만 발행된다")
        void pendingReservationCancelsDirectlyWithoutSaga() {
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.PENDING, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            orchestrator.startCancellation(100L, "user1");

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            verify(cancelSagaRepository, never()).save(any());
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("CONFIRMED 예매는 Saga를 시작하고 좌석 반환 명령을 발행한다")
        void confirmedReservationStartsSaga() {
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
            given(cancelSagaRepository.findByReservationId(100L)).willReturn(Optional.empty());
            given(cancelSagaRepository.save(any(CancelSaga.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            orchestrator.startCancellation(100L, "user1");

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLING);
            verify(cancelSagaRepository).save(any(CancelSaga.class));
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("공연 10일 이상 전이면 환불 금액이 전액으로 계산되어 Saga에 저장된다")
        void refundAmountIsFullWhenFarFromShowDate() {
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(15));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
            given(cancelSagaRepository.findByReservationId(100L)).willReturn(Optional.empty());

            ArgumentCaptor<CancelSaga> captor = ArgumentCaptor.forClass(CancelSaga.class);
            given(cancelSagaRepository.save(captor.capture()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            orchestrator.startCancellation(100L, "user1");

            assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("존재하지 않는 예매면 RESERVATION_NOT_FOUND 예외를 던진다")
        void throwsWhenReservationNotFound() {
            given(reservationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrator.startCancellation(999L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ReservationErrorCode.RESERVATION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("본인 예매가 아니면 RESERVATION_NOT_OWNED 예외를 던진다")
        void throwsWhenNotOwner() {
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            assertThatThrownBy(() -> orchestrator.startCancellation(100L, "other-user"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ReservationErrorCode.RESERVATION_NOT_OWNED.getMessage());
        }

        @Test
        @DisplayName("PENDING/CONFIRMED가 아닌 상태(CANCELLING 등)면 CANCELLATION_ALREADY_IN_PROGRESS 예외를 던진다")
        void throwsWhenNeitherPendingNorConfirmed() {
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CANCELLING, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            assertThatThrownBy(() -> orchestrator.startCancellation(100L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ReservationErrorCode.CANCELLATION_ALREADY_IN_PROGRESS.getMessage());
        }

        @Test
        @DisplayName("이미 진행 중인 Saga가 있으면 CANCELLATION_ALREADY_IN_PROGRESS 예외를 던진다 (멱등 방어)")
        void throwsWhenSagaAlreadyExists() {
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CONFIRMED, LocalDateTime.now().plusDays(10));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
            given(cancelSagaRepository.findByReservationId(100L))
                    .willReturn(Optional.of(sagaWithStatus(CancelSagaStatus.STARTED)));

            assertThatThrownBy(() -> orchestrator.startCancellation(100L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ReservationErrorCode.CANCELLATION_ALREADY_IN_PROGRESS.getMessage());
        }

        @Test
        @DisplayName("취소 가능 기간(공연 당일 이후)이 지났으면 CANCELLATION_DEADLINE_PASSED 예외를 던진다")
        void throwsWhenPastCancellationDeadline() {
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CONFIRMED, LocalDateTime.now().minusHours(1));   // 이미 지남
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
            given(cancelSagaRepository.findByReservationId(100L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrator.startCancellation(100L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ReservationErrorCode.CANCELLATION_DEADLINE_PASSED.getMessage());

            // 기한이 지났으니 예매 상태 자체가 바뀌면 안 된다.
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("onSeatReleased()")
    class OnSeatReleased {

        @Test
        @DisplayName("STARTED 상태의 Saga를 SEAT_RELEASED로 전이하고 환불 명령을 발행한다")
        void transitionsToSeatReleasedAndSendsRefundCommand() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.STARTED);
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));

            orchestrator.onSeatReleased(1L, 100L);

            assertThat(saga.isSeatReleased()).isTrue();
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("존재하지 않는 Saga면 조용히 무시한다")
        void doesNothingWhenSagaNotFound() {
            given(cancelSagaRepository.findById(999L)).willReturn(Optional.empty());

            orchestrator.onSeatReleased(999L, 100L);

            verify(outboxAppender, never()).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("이미 STARTED를 지난 Saga(중복 응답)는 조용히 무시한다")
        void ignoresDuplicateWhenAlreadyProgressed() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.SEAT_RELEASED);   // 이미 다음 단계
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));

            orchestrator.onSeatReleased(1L, 100L);

            verify(outboxAppender, never()).append(any(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("onPaymentRefunded()")
    class OnPaymentRefunded {

        @Test
        @DisplayName("SEAT_RELEASED 상태의 Saga를 완료시키고 예매를 CANCELLED로 만든다")
        void completesSagaAndCancelsReservation() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.SEAT_RELEASED);
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CANCELLING, LocalDateTime.now().plusDays(10));
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            orchestrator.onPaymentRefunded(1L, 100L);

            assertThat(saga.getStatus()).isEqualTo(CancelSagaStatus.COMPLETED);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        @DisplayName("존재하지 않는 Saga면 조용히 무시한다")
        void doesNothingWhenSagaNotFound() {
            given(cancelSagaRepository.findById(999L)).willReturn(Optional.empty());

            orchestrator.onPaymentRefunded(999L, 100L);

            verify(reservationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("SEAT_RELEASED 상태가 아니면(중복 응답) 조용히 무시한다")
        void ignoresDuplicateWhenNotSeatReleased() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.COMPLETED);   // 이미 완료됨
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));

            orchestrator.onPaymentRefunded(1L, 100L);

            verify(reservationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("예매가 존재하지 않아도 Saga 자체는 완료 처리된다 (부분 실패 방어)")
        void completesSagaEvenIfReservationMissing() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.SEAT_RELEASED);
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));
            given(reservationRepository.findById(100L)).willReturn(Optional.empty());

            orchestrator.onPaymentRefunded(1L, 100L);

            assertThat(saga.getStatus()).isEqualTo(CancelSagaStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("onPaymentRefundFailed()")
    class OnPaymentRefundFailed {

        @Test
        @DisplayName("SEAT_RELEASED 상태의 Saga를 COMPENSATING으로 전이하고 좌석 재점유 보상 명령을 발행한다")
        void startsCompensation() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.SEAT_RELEASED);
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));

            orchestrator.onPaymentRefundFailed(1L, 100L, "카드 한도 초과");

            assertThat(saga.isCompensating()).isTrue();
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("존재하지 않는 Saga면 조용히 무시한다")
        void doesNothingWhenSagaNotFound() {
            given(cancelSagaRepository.findById(999L)).willReturn(Optional.empty());

            orchestrator.onPaymentRefundFailed(999L, 100L, "reason");

            verify(outboxAppender, never()).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("SEAT_RELEASED 상태가 아니면(중복 응답) 조용히 무시한다")
        void ignoresDuplicateWhenNotSeatReleased() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.COMPENSATING);   // 이미 보상 중
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));

            orchestrator.onPaymentRefundFailed(1L, 100L, "reason");

            verify(outboxAppender, never()).append(any(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("onSeatReservedCompensated()")
    class OnSeatReservedCompensated {

        @Test
        @DisplayName("COMPENSATING 상태의 Saga를 FAILED로 만들고 예매를 CONFIRMED로 원상복구한다")
        void marksFailedAndRestoresReservation() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.COMPENSATING);
            Reservation reservation = reservationWithStatus(
                    ReservationStatus.CANCELLING, LocalDateTime.now().plusDays(10));
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));
            given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

            orchestrator.onSeatReservedCompensated(1L, 100L);

            assertThat(saga.getStatus()).isEqualTo(CancelSagaStatus.FAILED);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("존재하지 않는 Saga면 조용히 무시한다")
        void doesNothingWhenSagaNotFound() {
            given(cancelSagaRepository.findById(999L)).willReturn(Optional.empty());

            orchestrator.onSeatReservedCompensated(999L, 100L);

            verify(reservationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("COMPENSATING 상태가 아니면(중복 응답) 조용히 무시한다")
        void ignoresDuplicateWhenNotCompensating() {
            CancelSaga saga = sagaWithStatus(CancelSagaStatus.FAILED);   // 이미 처리됨
            given(cancelSagaRepository.findById(1L)).willReturn(Optional.of(saga));

            orchestrator.onSeatReservedCompensated(1L, 100L);

            verify(reservationRepository, never()).findById(any());
        }
    }
}