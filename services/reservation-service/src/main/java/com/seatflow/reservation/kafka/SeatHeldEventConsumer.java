package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatHeldEvent;
import com.seatflow.reservation.service.ReservationService;
import com.seatflow.reservation.service.command.CreateReservationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHeldEventConsumer {

    private final ReservationService reservationService;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.SEAT_HELD, groupId = "reservation-service")
    public void consume(String message) {
        SeatHeldEvent payload;
        try {
            EventEnvelope<SeatHeldEvent> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<SeatHeldEvent>>() {});
            payload = event.payload();
        } catch (Exception e) {
            log.error("Malformed seat.held skipped: {}", e.getMessage(), e);
            return;
        }

        reservationService.createReservation(
                new CreateReservationCommand(
                        payload.userId(),
                        payload.showId(),
                        payload.seatId(),
                        payload.price()   // 서버측 가격을 예매 원가로 저장
                ));
    }
}