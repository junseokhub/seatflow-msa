package com.seatflow.payment.service;

import com.seatflow.common.client.CouponClient;
import com.seatflow.common.client.ReservationClient;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.common.event.payment.PaymentFailedEvent;
import com.seatflow.common.event.payment.PaymentRefundFailedEvent;
import com.seatflow.common.event.payment.PaymentRefundedEvent;
import com.seatflow.common.client.ReservationClient.ReservationView;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
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
 * 결제 본체
 *
 * reservation 금액,상태 검증(2레이어 일부), 쿠폰 검증/할인 계산, 중복 COMPLETED 차단(부분 unique, 2레이어)을 담당한다.
 * 인프라 레벨 멱등성(1레이어, Redis 키)은 PaymentFacade가 이 메서드를 감싸 처리한다.
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
    private final CouponClient couponClient;
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

        paymentRepository
                .findByReservationIdAndStatus(command.reservationId(), PaymentStatus.COMPLETED)
                .ifPresent(p -> {
                    throw new BusinessException(
                            PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getStatus().value(),
                            PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
                });

        // 쿠폰 검증 -> 할인 계산. PG(mock)를 부르기 전에 최종 금액을 확정한다.
        // PG는 할인 개념을 몰라도 되고 몰라야 한다. payAmount만 받아 처리한다.
        BigDecimal payAmount = reservation.amount();
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (command.couponId() != null) {
            var validation = validateCoupon(command.couponId(), command.userId());
            discountAmount = validation.discountAmount();
            payAmount = reservation.amount().subtract(discountAmount);
        }

        Payment payment = Payment.builder()
                .reservationId(command.reservationId())
                .userId(command.userId())
                .amount(payAmount)
                .paymentMethod(command.paymentMethod())
                .couponId(command.couponId())
                .discountAmount(discountAmount)
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

            if (command.couponId() != null) {
                // 결제 성공 후에야 쿠폰을 확정한다. PG 승인이 실패했는데 쿠폰만
                // 소진되는 걸 막기 위해 반드시 이 순서(PG 처리 -> 성공 확인 -> 확정)를 지킨다.
                confirmCoupon(command.couponId(), command.userId(), command.reservationId());
            }

            outboxAppender.append(EventTopic.PAYMENT_COMPLETED, SOURCE, command.userId(),
                    new PaymentCompletedEvent(command.reservationId(), command.userId(), payAmount));
            log.info("Payment completed: paymentNumber={}", payment.getPaymentNumber());
        } else {
            payment.fail();
            // 쿠폰은 아직 확정 안 했으므로(성공 시에만 confirm) 별도 롤백이 필요 없다.
            // coupon-service 쪽 상태가 애초에 안 바뀌었다.
            outboxAppender.append(EventTopic.PAYMENT_FAILED, SOURCE, command.userId(),
                    new PaymentFailedEvent(command.reservationId(), command.userId(), payAmount));
            log.info("Payment failed: paymentNumber={}", payment.getPaymentNumber());
        }
        return payment;
    }

    private CouponClient.CouponValidationView validateCoupon(Long couponId, String userId) {
        try {
            var response = couponClient.validateCoupon(couponId, userId);
            var view = response.getData();
            if (view == null || !view.valid()) {
                throw new BusinessException(
                        PaymentErrorCode.COUPON_INVALID.getStatus().value(),
                        PaymentErrorCode.COUPON_INVALID.getMessage());
            }
            return view;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Coupon validation failed: couponId={}", couponId, e);
            throw new BusinessException(
                    PaymentErrorCode.COUPON_INVALID.getStatus().value(),
                    PaymentErrorCode.COUPON_INVALID.getMessage());
        }
    }

    private void confirmCoupon(Long couponId, String userId, Long reservationId) {
        try {
            couponClient.confirmCoupon(couponId, userId, reservationId);
        } catch (Exception e) {
            /**
             * 결제(PG)는 이미 성공했으므로 여기서 예외를 던져 트랜잭션을 롤백시키면 안 된다.
             * 결제는 됐는데 쿠폰 확정만 실패한 상태가 되더라도,
             * 돈이 이미 나간 사용자의 결제를 되돌리는 것보다 로그로 남기고 운영에서 확인하는 게 안전하다.
             * 13편에서 정한 보상마저 실패하면 사람이 봐야 한다 원칙과 같은 층의 판단이다.
             */
            log.error("Coupon confirm failed after successful payment, needs manual check: " +
                    "couponId={}, reservationId={}", couponId, reservationId, e);
        }
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
     * 쿠폰을 썼던 결제면 환불 성공 직후 쿠폰도 같이 복원한다.
     * 결과에 따라 성공/실패 응답을 발행한다.
     *
     * 멱등성: Payment.refund()가 이미 REFUNDED면 무시하는 상태 가드다.
     * 다만 같은 명령이 중복 도착해 이미 REFUNDED 처리된 뒤라면, 응답도 다시 성공으로 발행해 오케스트레이터가 놓친 응답을 다시 받을 수 있게 한다(at-least-once 전제).
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
            outboxAppender.append(EventTopic.PAYMENT_REFUNDED, SOURCE, String.valueOf(reservationId),
                    new PaymentRefundedEvent(sagaId, reservationId, payment.getRefundedAmount()));
            return;
        }

        boolean success = strategyRegistry
                .get(payment.getPaymentMethod())
                .refund(payment.getPaymentNumber(), refundAmount);

        if (success) {
            payment.refund(refundAmount);

            if (payment.getCouponId() != null) {
                // 환불 성공 직후 같은 서비스 안에서 바로 쿠폰 복원.
                // reservation은 쿠폰의 존재 자체를 몰라도 된다. 쿠폰 도메인 지식은 payment 안에만 있다.
                try {
                    couponClient.restoreCoupon(reservationId);
                } catch (Exception e) {
                    log.error("Coupon restore failed, needs manual recovery: reservationId={}, couponId={}",
                            reservationId, payment.getCouponId(), e);
                }
            }

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