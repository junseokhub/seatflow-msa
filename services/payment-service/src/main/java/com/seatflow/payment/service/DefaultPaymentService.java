package com.seatflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.common.event.payment.PaymentFailedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.payment.client.ReservationClient;
import com.seatflow.payment.client.ReservationView;
import com.seatflow.payment.domain.Outbox;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentStatus;
import com.seatflow.payment.exception.PaymentErrorCode;
import com.seatflow.payment.repository.OutboxRepository;
import com.seatflow.payment.repository.PaymentRepository;
import com.seatflow.payment.service.command.ProcessPaymentCommand;
import com.seatflow.payment.strategy.PaymentStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPaymentService implements PaymentService {

    private static final String SOURCE = "payment-service";
    private static final String STATUS_PENDING = "PENDING";

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper kafkaObjectMapper;
    private final PaymentStrategyRegistry strategyRegistry;
    private final ReservationClient reservationClient;

    /**
     * 결제 처리.
     *
     * 결제 금액 검증(정석): 결제 직전 reservation을 동기 조회해
     *   1) 예매가 결제 가능한 상태(PENDING)인지,
     *   2) 요청 금액이 예매의 실제 금액과 일치하는지
     * 를 확인한다. 금액의 진실 공급원은 reservation이며, 클라이언트가 보낸 금액은 대조용일 뿐
     * 신뢰하지 않는다. 실제 결제는 reservation의 금액으로 진행한다.
     *
     * 중복 결제 차단(멱등성)은 별도로 둔다.
     *   - 사전 확인: 이미 COMPLETED 결제가 있으면 거절.
     *   - 최종 방어: COMPLETED 저장 시 payments 부분 unique 제약이 동시 요청을 원자적으로 차단.
     */
    @Override
    @Transactional
    public Payment processPayment(ProcessPaymentCommand command) {
        // 1) reservation 동기 조회 + 검증
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
        // 실제 결제 금액은 서버측 값(reservation.amount)을 사용한다.
        BigDecimal payAmount = reservation.amount();

        // 2) 중복 결제 사전 확인
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
                paymentRepository.flush();   // COMPLETED 부분 unique를 커밋 전에 확인
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(
                        PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getStatus().value(),
                        PaymentErrorCode.PAYMENT_ALREADY_COMPLETED.getMessage());
            }
            appendOutbox(EventTopic.PAYMENT_COMPLETED, command.userId(),
                    new PaymentCompletedEvent(command.reservationId(), command.userId(), payAmount));
            log.info("Payment completed: paymentNumber={}", payment.getPaymentNumber());
        } else {
            payment.fail();
            appendOutbox(EventTopic.PAYMENT_FAILED, command.userId(),
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
            // reservation 조회 실패(네트워크/장애) — 결제를 진행하지 않는다.
            log.error("Reservation lookup failed: reservationId={}", reservationId, e);
            throw new BusinessException(
                    PaymentErrorCode.RESERVATION_NOT_FOUND.getStatus().value(),
                    PaymentErrorCode.RESERVATION_NOT_FOUND.getMessage());
        }
    }

    private void appendOutbox(String eventType, String messageKey, Object event) {
        EventEnvelope<?> envelope = EventEnvelope.of(eventType, SOURCE, event);
        outboxRepository.save(Outbox.builder()
                .eventId(envelope.eventId())
                .eventType(eventType)
                .messageKey(messageKey)
                .payload(toJson(envelope))
                .build());
    }

    private String toJson(Object o) {
        try {
            return kafkaObjectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException(
                    PaymentErrorCode.INTERNAL_ERROR.getStatus().value(),
                    PaymentErrorCode.INTERNAL_ERROR.getMessage());
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
}