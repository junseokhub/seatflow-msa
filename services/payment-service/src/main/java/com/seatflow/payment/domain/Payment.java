package com.seatflow.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, unique = true, updatable = false)
    private String paymentNumber;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private Long couponId;
    private BigDecimal discountAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = PaymentStatus.COMPLETED;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    /**
     * 환불 완료 처리(COMPLETED → REFUNDED). 실제 환불액을 기록한다.
     * 완료된 결제만 환불할 수 있고, 이미 REFUNDED면 멱등하게 무시한다(중복 환불 요청 대비).
     */
    public void refund(BigDecimal refundedAmount) {
        if (this.status == PaymentStatus.REFUNDED) {
            return;   // 이미 환불 → 멱등 무시
        }
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "완료된 결제만 환불할 수 있다: status=" + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAmount = refundedAmount;
    }

    /**
     * status 파라미터는 순수 단위 테스트에서 실제 저장(@PrePersist) 없이 원하는
     * 초기 상태를 바로 만들기 위한 것이다. 운영 코드는 이 파라미터를 넘기지 않고,
     * 실제 저장 시 @PrePersist가 무조건 PENDING으로 세팅하므로 운영 흐름에는
     * 영향이 없다. (coupon/seat/reservation에서 반복 적용한 것과 같은 패턴.)
     */
    @Builder
    private Payment(Long reservationId, String userId, BigDecimal amount,
                    PaymentMethod paymentMethod, Long couponId, BigDecimal discountAmount,
                    PaymentStatus status) {
        this.paymentNumber = UUID.randomUUID().toString();
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.couponId = couponId;
        this.discountAmount = discountAmount;
        this.status = status;
    }
}