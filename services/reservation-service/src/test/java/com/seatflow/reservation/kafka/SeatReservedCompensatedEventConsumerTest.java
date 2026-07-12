package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatReservedCompensatedEventConsumerTest {

    @Mock
    private CancelSagaOrchestrator orchestrator;

    private SeatReservedCompensatedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new SeatReservedCompensatedEventConsumer(orchestrator, realObjectMapper);
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 onSeatReservedCompensated에 위임된다")
    void validMessageDelegatesToOnSeatReservedCompensated() {
        String validMessage = """
                { "payload": { "sagaId": 1, "reservationId": 100 } }
                """;

        consumer.consume(validMessage);

        verify(orchestrator).onSeatReservedCompensated(1L, 100L);
    }

    @Test
    @DisplayName("깨진 메시지는 IllegalStateException을 던지고(DLQ 대상) 오케스트레이터는 호출하지 않는다")
    void malformedMessageThrowsForDlq() {
        assertThrows(IllegalStateException.class, () -> consumer.consume("not json"));

        verify(orchestrator, never()).onSeatReservedCompensated(anyLong(), anyLong());
    }
}