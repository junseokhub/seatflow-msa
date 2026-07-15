package com.seatflow.reservation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String showId;

    @Column(nullable = false)
    private Long seatId;

    /** 좌석 원가(서버가 seat.held로 받은 값). 결제 금액의 근거이며 클라이언트 입력을 신뢰하지 않는다. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "show_date", nullable = false)
    private LocalDateTime showDate;

    @Column(nullable = false, unique = true, updatable = false)
    private String reservationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = ReservationStatus.PENDING;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @Builder
    private Reservation(String userId, String showId, Long seatId,
                        BigDecimal amount, LocalDateTime showDate, ReservationStatus status) {
        this.userId = userId;
        this.showId = showId;
        this.seatId = seatId;
        this.amount = amount;
        this.showDate = showDate;
        this.reservationNumber = UUID.randomUUID().toString();
        this.status = status;
    }

    /**
     * 결제 완료(payment.completed)로 예매를 확정한다(PENDING ->CONFIRMED).
     * 이미 확정이면 멱등하게 무시하고, 취소된 예매는 확정할 수 없다.
     */
    public void confirm() {
        if (this.status == ReservationStatus.CONFIRMED) {
            return;
        }
        if (this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "취소된 예매는 확정할 수 없다: reservationNumber=" + reservationNumber);
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * 취소 Saga 시작. CONFIRMED만 취소할 수 있다(CANCELLING으로 전이).
     */
    public void startCancelling() {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "확정된 예매만 취소할 수 있다: status=" + status);
        }
        this.status = ReservationStatus.CANCELLING;
    }


    /**
     * 결제 전 즉시 취소(PENDING ->CANCELLED). Saga 없이 직접 확정한다.
     */
    public void cancelPending() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException(
                    "결제 대기 상태만 이 방식으로 취소할 수 있다: status=" + status);
        }
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * 취소 Saga 보상 완료로 예매를 원상복구한다(CANCELLING ->CONFIRMED).
     * 환불이 실패해 좌석을 다시 점유시켰으니, 예매도 확정 상태로 되돌린다.
     * CANCELLING이 아니면(이미 처리된 중복 호출) 조용히 무시한다.
     */
    public void revertCancelling() {
        if (this.status != ReservationStatus.CANCELLING) {
            return;   // 멱등 무시
        }
        this.status = ReservationStatus.CONFIRMED;
    }
}