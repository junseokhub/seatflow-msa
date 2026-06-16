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
        try {
            EventEnvelope<SeatHeldEvent> event = kafkaObjectMapper.readValue(
                    message,
                    new TypeReference<EventEnvelope<SeatHeldEvent>>() {}
            );
            log.info("Received SeatHeldEvent: eventId={}, seatId={}",
                    event.eventId(), event.payload().seatId());

            reservationService.createReservation(
                    new CreateReservationCommand(
                            event.payload().userId(),
                            event.payload().showId(),
                            event.payload().seatId()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to process SeatHeldEvent: {}", e.getMessage(), e);
        }
    }
}