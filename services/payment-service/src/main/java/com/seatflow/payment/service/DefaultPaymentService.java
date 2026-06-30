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
    private final OutboxRepository outboxRepository;
    private final ObjectMapper kafkaObjectMapper;
    private final PaymentStrategyRegistry strategyRegistry;
    private final ReservationClient reservationClient;

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