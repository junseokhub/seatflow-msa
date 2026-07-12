package com.seatflow.reservation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 취소 Saga 상태. 예매 하나의 취소 흐름을 추적한다.
 * reservation이 오케스트레이터로서 이 상태를 갱신하며 단계를 진행한다.
 *
 * 멱등성: reservationId에 unique를 걸어 같은 예매의 취소 Saga가 중복 생성되지 않게 한다.
 */
@Entity
@Table(name = "cancel_saga",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cancel_saga_reservation",
                columnNames = "reservation_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CancelSaga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long seatId;

    @Column(nullable = false)
    private String showId;

    /** 환불 예정 금액(취소 수수료 반영). 오케스트레이터가 showDate 기준으로 계산해 담는다. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CancelSagaStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = CancelSagaStatus.STARTED;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    private CancelSaga(Long reservationId, String userId, Long seatId,
                       String showId, BigDecimal refundAmount, CancelSagaStatus status) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.seatId = seatId;
        this.showId = showId;
        this.refundAmount = refundAmount;
        this.status = status;
    }

    public void markSeatReleased() {
        this.status = CancelSagaStatus.SEAT_RELEASED;
    }

    public void markRefunded() {
        this.status = CancelSagaStatus.REFUNDED;
    }

    public void markCompleted() {
        this.status = CancelSagaStatus.COMPLETED;
    }

    public void markCompensating() {
        this.status = CancelSagaStatus.COMPENSATING;
    }

    public void markFailed() {
        this.status = CancelSagaStatus.FAILED;
    }

    public boolean isStarted() {
        return this.status == CancelSagaStatus.STARTED;
    }

    public boolean isSeatReleased() {
        return this.status == CancelSagaStatus.SEAT_RELEASED;
    }

    public boolean isCompensating() {
        return this.status == CancelSagaStatus.COMPENSATING;
    }
}