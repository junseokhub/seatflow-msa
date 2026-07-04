package com.seatflow.seat.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatReserveCompensationCommand;
import com.seatflow.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 취소 Saga 보상 명령(환불 실패로 좌석을 다시 점유). 처리 후 seat.reserved.compensated로 응답한다.
 * seat.reserve()도 멱등하게 설계돼 있어 중복 도착해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReserveCompensationCommandConsumer {

    private final SeatService seatService;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.SEAT_RESERVE_COMPENSATION_COMMAND, groupId = "seat-service")
    public void consume(String message) {
        SeatReserveCompensationCommand command;
        try {
            EventEnvelope<SeatReserveCompensationCommand> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<SeatReserveCompensationCommand>>() {});
            command = event.payload();
        } catch (Exception e) {
            log.error("Malformed seat.reserve.compensation.command skipped: {}", e.getMessage(), e);
            return;
        }

        seatService.reserveSeatForCompensation(
                command.sagaId(), command.reservationId(), command.showId(), command.seatId());
    }
}