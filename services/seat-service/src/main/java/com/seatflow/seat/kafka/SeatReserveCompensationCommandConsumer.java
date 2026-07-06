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
 *
 * 이 명령은 보상 경로의 마지막 단계다. 여기서 예외가 나면 CancelSaga가 COMPENSATING에서
 * 영원히 멈춘다(더 보상할 다음 단계가 없어 재시도 외엔 복구 수단이 없다). 그래서 실패해도
 * 예외를 삼켜 컨슈머가 멈추지 않게 하고, 로그로 남겨 운영에서 수동 개입할 수 있게 한다.
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
            log.error("Malformed seat.reserve.compensation.command: {}", e.getMessage());
            throw new IllegalStateException("Malformed seat.reserve.compensation.command", e);
        }

        try {
            seatService.reserveSeatForCompensation(
                    command.sagaId(), command.reservationId(), command.showId(), command.seatId());
        } catch (Exception e) {
            // TODO: 보상의 보상은 없다. 실패 시 CancelSaga가 COMPENSATING에 멈춘 채로
            // 남는다 — 이건 사람이 개입해야 하는 최종 실패 케이스로 취급한다.
            // 운영 알림(Discord webhook 등) 연동은 향후 보강 지점이다.
            log.error("Seat compensation reserve failed unexpectedly: sagaId={}, seatId={}",
                    command.sagaId(), command.seatId(), e);
        }
    }
}