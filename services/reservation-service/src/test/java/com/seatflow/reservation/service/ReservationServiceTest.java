package com.seatflow.reservation.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import com.seatflow.reservation.exception.ReservationErrorCode;
import com.seatflow.reservation.repository.ReservationRepository;
import com.seatflow.reservation.service.command.CreateReservationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private OutboxAppender outboxAppender;
    @Mock
    private ReservationRepository reservationRepository;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(outboxAppender, reservationRepository);
    }

    private Reservation reservationWithStatus(ReservationStatus status) {
        return Reservation.builder()
                .userId("user1").showId("show-1").seatId(1L)
                .amount(BigDecimal.valueOf(50000)).showDate(LocalDateTime.now().plusDays(10))
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("createReservation()")
    class CreateReservation {

        @Test
        @DisplayName("커맨드를 받아 Reservation을 만들고 저장한다")
        void createsAndSavesReservation() {
            CreateReservationCommand command = new CreateReservationCommand(
                    "user1", "show-1", 1L, BigDecimal.valueOf(50000), LocalDateTime.now().plusDays(10));
            given(reservationRepository.save(any(Reservation.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            Reservation result = reservationService.createReservation(command);

            assertThat(result.getUserId()).isEqualTo("user1");
            assertThat(result.getShowId()).isEqualTo("show-1");
            assertThat(result.getSeatId()).isEqualTo(1L);
            verify(reservationRepository).save(any(Reservation.class));
        }
    }

    @Nested
    @DisplayName("confirmReservation()")
    class ConfirmReservation {

        @Test
        @DisplayName("PENDING 예매를 확정하고 reservation.confirmed 이벤트를 발행한다")
        void confirmsPendingReservationAndPublishesEvent() {
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);
            given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

            reservationService.confirmReservation(1L);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
            verify(outboxAppender).append(any(), any(), any(), any());
        }

        @Test
        @DisplayName("이미 CONFIRMED인 예매는 재확정해도 이벤트를 다시 발행하지 않는다 (중복 payment.completed 방어)")
        void doesNotRepublishWhenAlreadyConfirmed() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);
            given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

            reservationService.confirmReservation(1L);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
            verify(outboxAppender, never()).append(any(), any(), any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 예매면 조용히 무시한다 (예외 없음)")
        void doesNothingWhenReservationNotFound() {
            given(reservationRepository.findById(999L)).willReturn(Optional.empty());

            reservationService.confirmReservation(999L);

            verify(outboxAppender, never()).append(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getReservation()")
    class GetReservation {

        @Test
        @DisplayName("존재하는 예매를 반환한다")
        void returnsExistingReservation() {
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);
            given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

            Reservation result = reservationService.getReservation(1L);

            assertThat(result).isEqualTo(reservation);
        }

        @Test
        @DisplayName("존재하지 않으면 RESERVATION_NOT_FOUND 예외를 던진다")
        void throwsWhenNotFound() {
            given(reservationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.getReservation(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ReservationErrorCode.RESERVATION_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("getUserReservations()")
    class GetUserReservations {

        @Test
        @DisplayName("해당 유저의 예매 목록을 반환한다")
        void returnsListForUser() {
            given(reservationRepository.findByUserId("user1"))
                    .willReturn(List.of(reservationWithStatus(ReservationStatus.PENDING)));

            List<Reservation> result = reservationService.getUserReservations("user1");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("cancelReservation()")
    class CancelReservation {

        @Test
        @DisplayName("정상적으로 취소한다")
        void cancelsReservation() {
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);
            given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

            reservationService.cancelReservation(1L);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 취소된 예매면 RESERVATION_ALREADY_CANCELLED 예외를 던진다")
        void throwsWhenAlreadyCancelled() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CANCELLED);
            given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));

            assertThatThrownBy(() -> reservationService.cancelReservation(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ReservationErrorCode.RESERVATION_ALREADY_CANCELLED.getMessage());
        }
    }
}