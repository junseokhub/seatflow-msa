package com.seatflow.seat.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.seat.kafka.ReservationConfirmedEventConsumer;
import com.seatflow.seat.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 다른 세 컨슈머와 달리 이 컨슈머는 catch 없이 seatService.reserveSeat을 그대로
 * 호출한다(제공된 코드에서 try-catch가 없었다) — 실패 시 예외가 그대로 Kafka
 * 리스너 컨테이너까지 전파되어 재시도/DLQ 대상이 된다는 뜻이다. 다른 컨슈머들이
 * "실패를 삼키는" 것과 의도적으로 다른 정책이라면, 그 차이 자체도 테스트로
 * 명시해둘 가치가 있다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationConfirmedEventConsumerTest {

    @Mock
    private SeatService seatService;

    private ReservationConfirmedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new ReservationConfirmedEventConsumer(seatService, realObjectMapper);
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 reserveSeat에 위임된다")
    void validMessageDelegatesToReserveSeat() {
        String validMessage = """
                {
                  "payload": {
                    "showId": "show-1",
                    "seatId": 3,
                    "userId": "user1"
                  }
                }
                """;

        consumer.consume(validMessage);

        verify(seatService).reserveSeat("show-1", 3L, "user1");
    }

    @Test
    @DisplayName("깨진 메시지는 IllegalStateException을 던지고 서비스는 호출하지 않는다")
    void malformedMessageThrows() {
        assertThrows(IllegalStateException.class, () -> consumer.consume("broken"));

        verify(seatService, never()).reserveSeat(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("서비스 처리 중 예외가 나도 컨슈머가 삼키고 로그만 남긴다 (다른 컨슈머들과 통일된 정책)")
    void serviceFailureIsSwallowedNotRethrown() {
        String validMessage = """
                {
                  "payload": {
                    "showId": "show-1",
                    "seatId": 3,
                    "userId": "user1"
                  }
                }
                """;
        doThrow(new RuntimeException("DB error"))
                .when(seatService).reserveSeat("show-1", 3L, "user1");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> consumer.consume(validMessage));
    }
}