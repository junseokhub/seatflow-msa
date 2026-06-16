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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

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

    @Builder
    private Payment(Long reservationId, String userId, BigDecimal amount, PaymentMethod paymentMethod) {
        this.paymentNumber = UUID.randomUUID().toString();
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }
}