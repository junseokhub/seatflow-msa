package com.seatflow.seat.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatReleaseCommand;
import com.seatflow.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 취소 Saga의 좌석 반환 명령을 받아 처리한다. 처리 후 seat.released로 응답한다.
 * seat.release()는 이미 멱등하게 설계돼 있어(이미 AVAILABLE이면 무시) 명령이 중복
 * 도착해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReleaseCommandConsumer {

    private final SeatService seatService;
    private final ObjectMapper kafkaObjectMapper;

    @KafkaListener(topics = EventTopic.SEAT_RELEASE_COMMAND, groupId = "seat-service")
    public void consume(String message) {
        SeatReleaseCommand command;
        try {
            EventEnvelope<SeatReleaseCommand> event = kafkaObjectMapper.readValue(
                    message, new TypeReference<EventEnvelope<SeatReleaseCommand>>() {});
            command = event.payload();
        } catch (Exception e) {
            log.error("Malformed seat.release.command skipped: {}", e.getMessage(), e);
            return;
        }

        seatService.releaseSeatForCancellation(
                command.sagaId(), command.reservationId(), command.showId(), command.seatId());
    }
}