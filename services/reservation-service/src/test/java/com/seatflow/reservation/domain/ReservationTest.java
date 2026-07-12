package com.seatflow.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private Reservation reservationWithStatus(ReservationStatus status) {
        return Reservation.builder()
                .userId("user1")
                .showId("show-1")
                .seatId(1L)
                .amount(BigDecimal.valueOf(50000))
                .showDate(LocalDateTime.now().plusDays(10))
                .status(status)
                .build();
    }

    @Test
    @DisplayName("생성 직후 reservationNumber가 자동으로 채워진다")
    void reservationNumberIsGeneratedOnCreation() {
        Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

        assertThat(reservation.getReservationNumber()).isNotBlank();
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("PENDING 상태를 CONFIRMED로 확정한다")
        void confirmsPendingReservation() {
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            reservation.confirm();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("이미 CONFIRMED면 멱등하게 무시한다")
        void confirmIsIdempotent() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);

            reservation.confirm();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("CANCELLED 상태는 확정할 수 없다")
        void cannotConfirmCancelledReservation() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CANCELLED);

            assertThatThrownBy(reservation::confirm)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("startCancelling()")
    class StartCancelling {

        @Test
        @DisplayName("CONFIRMED 상태를 CANCELLING으로 전이한다")
        void transitionsConfirmedToCancelling() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);

            reservation.startCancelling();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLING);
        }

        @Test
        @DisplayName("CONFIRMED가 아니면(PENDING) 예외를 던진다")
        void throwsWhenNotConfirmed() {
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            assertThatThrownBy(reservation::startCancelling)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("cancelPending()")
    class CancelPending {

        @Test
        @DisplayName("PENDING 상태를 CANCELLED로 즉시 전이한다")
        void cancelsPendingReservation() {
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            reservation.cancelPending();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        @DisplayName("PENDING이 아니면(CONFIRMED) 예외를 던진다 (이 경로는 결제 전 취소 전용)")
        void throwsWhenNotPending() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);

            assertThatThrownBy(reservation::cancelPending)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("revertCancelling()")
    class RevertCancelling {

        @Test
        @DisplayName("CANCELLING 상태를 CONFIRMED로 원상복구한다 (보상 완료)")
        void revertsCancellingToConfirmed() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CANCELLING);

            reservation.revertCancelling();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        @DisplayName("CANCELLING이 아니면 조용히 무시한다 (중복 호출 방어)")
        void doesNothingWhenNotCancelling() {
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);

            reservation.revertCancelling();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }
    }

    @Test
    @DisplayName("cancel()은 상태와 무관하게 CANCELLED로 만든다 (Saga 최종 완료 전용, 무조건 전이)")
    void cancelAlwaysSetsCancelled() {
        Reservation reservation = reservationWithStatus(ReservationStatus.CANCELLING);

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }
}