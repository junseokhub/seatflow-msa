package com.seatflow.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CancelSagaTest {

    private CancelSaga sagaWithStatus(CancelSagaStatus status) {
        return CancelSaga.builder()
                .reservationId(1L)
                .userId("user1")
                .seatId(1L)
                .showId("show-1")
                .refundAmount(BigDecimal.valueOf(9000))
                .status(status)
                .build();
    }

    @Test
    @DisplayName("생성 직후 STARTED 상태다")
    void startsAsStarted() {
        CancelSaga saga = sagaWithStatus(CancelSagaStatus.STARTED);

        assertThat(saga.isStarted()).isTrue();
        assertThat(saga.isSeatReleased()).isFalse();
        assertThat(saga.isCompensating()).isFalse();
    }

    @Test
    @DisplayName("markSeatReleased()는 SEAT_RELEASED로 전이한다")
    void markSeatReleasedTransitions() {
        CancelSaga saga = sagaWithStatus(CancelSagaStatus.STARTED);

        saga.markSeatReleased();

        assertThat(saga.isSeatReleased()).isTrue();
        assertThat(saga.isStarted()).isFalse();
    }

    @Test
    @DisplayName("markRefunded() 후 markCompleted()로 정상 경로를 완주한다")
    void completesNormalPath() {
        CancelSaga saga = sagaWithStatus(CancelSagaStatus.SEAT_RELEASED);

        saga.markRefunded();
        saga.markCompleted();

        assertThat(saga.getStatus()).isEqualTo(CancelSagaStatus.COMPLETED);
    }

    @Test
    @DisplayName("markCompensating() 후 markFailed()로 보상 경로를 완주한다")
    void completesCompensationPath() {
        CancelSaga saga = sagaWithStatus(CancelSagaStatus.SEAT_RELEASED);

        saga.markCompensating();

        assertThat(saga.isCompensating()).isTrue();

        saga.markFailed();

        assertThat(saga.getStatus()).isEqualTo(CancelSagaStatus.FAILED);
        assertThat(saga.isCompensating()).isFalse();
    }
}