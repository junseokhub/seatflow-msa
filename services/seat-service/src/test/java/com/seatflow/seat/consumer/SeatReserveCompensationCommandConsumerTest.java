package com.seatflow.seat.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.seat.kafka.SeatReserveCompensationCommandConsumer;
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
class SeatReserveCompensationCommandConsumerTest {

    @Mock
    private SeatService seatService;

    private SeatReserveCompensationCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new SeatReserveCompensationCommandConsumer(seatService, realObjectMapper);
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 reserveSeatForCompensation에 위임된다")
    void validMessageDelegatesToCompensation() {
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

        verify(seatService).reserveSeatForCompensation(1L, 2L, "show-1", 3L);
    }

    @Test
    @DisplayName("깨진 메시지는 IllegalStateException을 던지고 서비스는 호출하지 않는다")
    void malformedMessageThrows() {
        assertThrows(IllegalStateException.class, () -> consumer.consume("broken"));

        verify(seatService, never())
                .reserveSeatForCompensation(anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("보상 처리 중 예외가 나도 컨슈머가 삼키고 로그만 남긴다 — 보상의 보상은 없다")
    void compensationFailureIsSwallowed() {
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
        doThrow(new RuntimeException("unexpected failure"))
                .when(seatService).reserveSeatForCompensation(1L, 2L, "show-1", 3L);

        // 이 실패는 CancelSaga가 COMPENSATING에 멈춘 채로 남는, 사람이 개입해야 하는
        // 최종 실패 케이스로 의도적으로 다룬다 — 컨슈머 자체는 죽지 않아야 한다.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> consumer.consume(validMessage));
    }
}