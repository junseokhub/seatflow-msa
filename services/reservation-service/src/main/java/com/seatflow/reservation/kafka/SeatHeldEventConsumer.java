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
            // 역직렬화 실패(poison message)는 재시도해도 똑같이 실패한다.
            // 여기서 삼키고 return하면 공통 에러 핸들러(DLQ)가 개입할 기회 자체가 없어진다.
            // 던져서 재시도(3번) 후 DLQ(seat.held.DLT)로 격리되게 한다.
            log.error("Malformed seat.held: {}", e.getMessage());
            throw new IllegalStateException("Malformed seat.held", e);
        }

        reservationService.createReservation(
                new CreateReservationCommand(
                        payload.userId(),
                        payload.showId(),
                        payload.seatId(),
                        payload.price(),
                        payload.showDate()
                ));
    }
}