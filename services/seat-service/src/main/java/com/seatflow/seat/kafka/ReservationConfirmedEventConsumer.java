package com.seatflow.seat.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.reservation.ReservationConfirmedEvent;
import com.seatflow.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * reservation.confirmed를 받아 좌석을 확정 점유(RESERVED)한다.
 * 임시 점유(Redis hold)를 영구 확정(DB RESERVED)으로 넘기고, 남은 hold 키를 정리한다.
 * 좌석 확정은 되돌릴 필요가 없는 정상 흐름이라 단순 이벤트로 처리한다(Saga 아님).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationConfirmedEventConsumer {

    private final SeatService seatService;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.RESERVATION_CONFIRMED, groupId = "seat-service")
    public void consume(String message) {
        ReservationConfirmedEvent payload;
        try {
            EventEnvelope<ReservationConfirmedEvent> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<ReservationConfirmedEvent>>() {
                    });
            payload = event.payload();
        } catch (Exception e) {
            log.error("Malformed reservation.confirmed: {}", e.getMessage());
            throw new IllegalStateException("Malformed reservation.confirmed", e);
        }

        try {
            seatService.reserveSeat(payload.showId(), payload.seatId(), payload.userId());
        } catch (Exception e) {
            log.error("Seat reserve confirmation failed unexpectedly: showId={}, seatId={}",
                    payload.showId(), payload.seatId(), e);
        }
    }
}