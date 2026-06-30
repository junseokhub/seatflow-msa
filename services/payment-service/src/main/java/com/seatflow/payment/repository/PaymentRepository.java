package com.seatflow.payment.repository;

import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByReservationId(Long reservationId);

    // 비즈니스 멱등성: 이미 완료된 결제 조회 (중복 결제 차단/멱등 응답용)
    Optional<Payment> findByReservationIdAndStatus(Long reservationId, PaymentStatus status);
}