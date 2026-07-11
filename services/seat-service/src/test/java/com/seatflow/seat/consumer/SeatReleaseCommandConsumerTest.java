package com.seatflow.seat.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.seat.kafka.SeatReleaseCommandConsumer;
import com.seatflow.seat.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatReleaseCommandConsumerTest {

    @Mock
    private SeatService seatService;

    private SeatReleaseCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new SeatReleaseCommandConsumer(seatService, realObjectMapper);
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 releaseSeatForCancellation에 위임된다")
    void validMessageDelegatesToReleaseSeatForCancellation() {
        String validMessage = """
                {
                  "payload": {
                    "sagaId": 1,
                    "reservationId": 2,
                    "showId": "show-1",
                    "seatId": 3
                  }
                }
                """;

        consumer.consume(validMessage);

        verify(seatService).releaseSeatForCancellation(1L, 2L, "show-1", 3L);
    }

    @Test
    @DisplayName("깨진 메시지는 IllegalStateException을 던지고 서비스는 호출하지 않는다")
    void malformedMessageThrows() {
        String malformed = "not json at all";

        assertThrows(IllegalStateException.class, () -> consumer.consume(malformed));

        verify(seatService, never()).releaseSeatForCancellation(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("서비스 처리 중 예외가 나도 컨슈머는 삼키고 로그만 남긴다 (컨슈머 자체가 죽지 않음)")
    void serviceFailureIsSwallowedNotRethrown() {
        String validMessage = """
                {
                  "payload": {
                    "sagaId": 1,
                    "reservationId": 2,
                    "showId": "show-1",
                    "seatId": 3
                  }
                }
                """;
        doThrow(new RuntimeException("DB connection lost"))
                .when(seatService).releaseSeatForCancellation(1L, 2L, "show-1", 3L);

        // 컨슈머 코드가 이 예외를 catch해서 로그만 남기고 정상 종료하므로,
        // consume() 자체는 예외를 던지지 않아야 한다 — Kafka 리스너 컨테이너까지
        // 예외가 전파되면 재시도/DLQ 로직이 다시 도는데, 지금 이 실패는 그런
        // 케이스가 아니라 "사람이 개입해야 하는 최종 실패"로 의도적으로 삼킨 것이다.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> consumer.consume(validMessage));
    }
}