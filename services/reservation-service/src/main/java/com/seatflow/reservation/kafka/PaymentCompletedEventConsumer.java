package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.payment.PaymentCompletedEvent;
import com.seatflow.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * payment.completed를 받아 예매를 확정(PENDING → CONFIRMED)한다.
 * 결제 성공 사실이 예매 확정으로 이어지는, 예매 흐름의 마지막 연결 고리다.
 *
 * 멱등성: 같은 payment.completed가 중복 도착해도 Reservation.confirm()이
 * 이미 CONFIRMED면 무시하므로(상태 전이 멱등) 예매가 어긋나지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {

    private static final String GROUP = "reservation-service";

    private final ReservationService reservationService;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.PAYMENT_COMPLETED, groupId = GROUP)
    public void consume(String message) {
        EventEnvelope<PaymentCompletedEvent> event;
        try {
            event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentCompletedEvent>>() {});
        } catch (Exception e) {
            log.error("Malformed payment.completed: {}", e.getMessage());
            throw new IllegalStateException("Malformed payment.completed", e);
        }

        PaymentCompletedEvent payload = event.payload();
        reservationService.confirmReservation(payload.reservationId());
        log.info("Reservation confirmed by payment: reservationId={}", payload.reservationId());
    }
}