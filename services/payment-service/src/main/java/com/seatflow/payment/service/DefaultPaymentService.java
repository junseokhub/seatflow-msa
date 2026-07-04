package com.seatflow.payment.service;

import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.common.event.payment.PaymentFailedEvent;
import com.seatflow.common.event.payment.PaymentRefundFailedEvent;
import com.seatflow.common.event.payment.PaymentRefundedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.payment.client.ReservationClient;
import com.seatflow.payment.client.ReservationView;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentStatus;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.repository.PaymentRepository;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import com.seatflow.payment.strategy.PaymentStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 결제 본체. reservation 금액·상태 검증(2층 일부)과 중복 COMPLETED 차단(부분 unique, 2층)을 담당한다.
 * 인프라 레벨 멱등성(1층, Redis 키)은 PaymentFacade가 이 메서드를 감싸 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPaymentService implements PaymentService {

    private static final String SOURCE = "payment-service";
    private static final String STATUS_PENDING = "PENDING";

    private final PaymentRepository paymentRepository;
    private final PaymentStrategyRegistry strategyRegistry;
    private final ReservationClient reservationClient;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional
    public Payment executePayment(ProcessPaymentCommand command) {
        ReservationView reservation = fetchReservation(command.reservationId());
        if (!STATUS_PENDING.equals(reservation.status())) {
            throw new BusinessException(
                    PaymentErrorCode.RESERVATION_NOT_PAYABLE.getStatus().value(),
                    PaymentErrorCode.RESERVATION_NOT_PAYABLE.getMessage());
        }
        if (reservation.amount().compareTo(command.amount()) != 0) {
            throw new BusinessException(
                    PaymentErrorCode.AMOUNT_MISMATCH.getStatus().value(),
                    PaymentErrorCode.AMOUNT_MISMATCH.getMessage());
        }
        BigDecimal payAmount = reservation.amount();

        paymentRepository
                .findByReservationIdAndStatus(command.reservationId(), PaymentStatus.COMPLETED)
                .ifPresent(p -> {
                    throw new BusinessException(
                            PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getStatus().value(),
                            PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
                });

        Payment payment = Payment.builder()
                .reservationId(command.reservationId())
                .userId(command.userId())
                .amount(payAmount)
                .paymentMethod(command.paymentMethod())
                .build();
        paymentRepository.save(payment);

        boolean success = strategyRegistry
                .get(command.paymentMethod())
                .process(payment.getPaymentNumber(), payAmount);

        if (success) {
            payment.complete();
            try {
                paymentRepository.flush();
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(
                        PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getStatus().value(),
                        PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
            }
            outboxAppender.append(EventTopic.PAYMENT_COMPLETED, SOURCE, command.userId(),
                    new PaymentCompletedEvent(command.reservationId(), command.userId(), payAmount));
            log.info("Payment completed: paymentNumber={}", payment.getPaymentNumber());
        } else {
            payment.fail();
            outboxAppender.append(EventTopic.PAYMENT_FAILED, SOURCE, command.userId(),
                    new PaymentFailedEvent(command.reservationId(), command.userId(), payAmount));
            log.info("Payment failed: paymentNumber={}", payment.getPaymentNumber());
        }
        return payment;
    }

    private ReservationView fetchReservation(Long reservationId) {
        try {
            ReservationView view = reservationClient.getReservation(reservationId).getData();
            if (view == null) {
                throw new BusinessException(
                        PaymentErrorCode.RESERVATION_NOT_FOUND.getStatus().value(),
                        PaymentErrorCode.RESERVATION_NOT_FOUND.getMessage());
            }
            return view;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Reservation lookup failed: reservationId={}", reservationId, e);
            throw new BusinessException(
                    PaymentErrorCode.RESERVATION_NOT_FOUND.getStatus().value(),
                    PaymentErrorCode.RESERVATION_NOT_FOUND.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        PaymentErrorCode.PAYMENT_NOT_FOUND.getStatus().value(),
                        PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage()
                ));
    }

    /**
     * 취소 Saga의 환불 명령을 처리한다. 결제한 수단(PaymentStrategy)으로 환불을 라우팅하고,
     * 결과에 따라 성공/실패 응답을 발행한다.
     *
     * 멱등성: Payment.refund()가 이미 REFUNDED면 무시하는 상태 가드다. 다만 같은 명령이
     * 중복 도착해 이미 REFUNDED 처리된 뒤라면, 응답도 다시 성공으로 발행해 오케스트레이터가
     * 놓친 응답을 다시 받을 수 있게 한다(at-least-once 전제).
     */
    @Transactional
    public void executeRefund(Long sagaId, Long reservationId, BigDecimal refundAmount) {
        Payment payment = paymentRepository
                .findByReservationIdAndStatus(reservationId, PaymentStatus.COMPLETED)
                .orElseGet(() -> paymentRepository
                        .findByReservationIdAndStatus(reservationId, PaymentStatus.REFUNDED)
                        .orElse(null));

        if (payment == null) {
            log.warn("Payment not found for refund: sagaId={}, reservationId={}", sagaId, reservationId);
            outboxAppender.append(EventTopic.PAYMENT_REFUND_FAILED, SOURCE, String.valueOf(reservationId),
                    new PaymentRefundFailedEvent(sagaId, reservationId, "결제 내역을 찾을 수 없음"));
            return;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            // 이미 환불 완료된 상태(중복 명령) → 성공 응답 재발행
            outboxAppender.append(EventTopic.PAYMENT_REFUNDED, SOURCE, String.valueOf(reservationId),
                    new PaymentRefundedEvent(sagaId, reservationId, payment.getRefundedAmount()));
            return;
        }

        boolean success = strategyRegistry
                .get(payment.getPaymentMethod())
                .refund(payment.getPaymentNumber(), refundAmount);

        if (success) {
            payment.refund(refundAmount);
            outboxAppender.append(EventTopic.PAYMENT_REFUNDED, SOURCE, String.valueOf(reservationId),
                    new PaymentRefundedEvent(sagaId, reservationId, refundAmount));
            log.info("Refund completed: sagaId={}, paymentNumber={}", sagaId, payment.getPaymentNumber());
        } else {
            outboxAppender.append(EventTopic.PAYMENT_REFUND_FAILED, SOURCE, String.valueOf(reservationId),
                    new PaymentRefundFailedEvent(sagaId, reservationId, "PG 환불 승인 거절"));
            log.warn("Refund failed: sagaId={}, paymentNumber={}", sagaId, payment.getPaymentNumber());
        }
    }
}