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
 *
 * 주의: 좌석 반환은 지금 구조상 실패 응답 이벤트가 따로 없다(성공만 가정).
 * seat.release()가 도메인 상태 가드로 항상 성공하는 연산이라 실패 케이스가
 * 거의 없긴 하지만, 예상 못한 예외(DB 오류 등)가 나면 이 명령도 응답을 못 보내
 * Saga가 멈출 수 있다. 우선은 로그를 남기고 예외를 삼켜(재시도로 넘기지 않고)
 * 운영에서 알아챌 수 있게 한다. 실패 응답 이벤트(seat.release.failed) 추가는
 * 향후 보강 지점이다.
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
            log.error("Malformed seat.release.command: {}", e.getMessage());
            throw new IllegalStateException("Malformed seat.release.command", e);
        }

        try {
            seatService.releaseSeatForCancellation(
                    command.sagaId(), command.reservationId(), command.showId(), command.seatId());
        } catch (Exception e) {
            // TODO: seat.release.failed 응답 이벤트 추가해 오케스트레이터가
            // 이 실패도 인지하고 보상/재시도를 판단할 수 있게 보강 필요.
            log.error("Seat release failed unexpectedly: sagaId={}, seatId={}",
                    command.sagaId(), command.seatId(), e);
        }
    }
}