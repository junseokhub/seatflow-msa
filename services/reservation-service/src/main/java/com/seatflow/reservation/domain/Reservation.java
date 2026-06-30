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
    private Reservation(String userId, String showId, Long seatId, BigDecimal amount) {
        this.userId = userId;
        this.showId = showId;
        this.seatId = seatId;
        this.amount = amount;
        this.reservationNumber = UUID.randomUUID().toString();
    }

    /**
     * 결제 완료(payment.completed)로 예매를 확정한다(PENDING → CONFIRMED).
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
}