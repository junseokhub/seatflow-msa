package com.seatflow.seat.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.seat.kafka.ShowCreatedEventConsumer;
import com.seatflow.seat.service.SeatGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 컨슈머의 판단 부분(malformed 메시지면 예외를 던져 DLQ로 보내지게 하는 것, 정상이면 서비스에 위임하는 것)만 검증한다.
 * 진짜 Kafka를 띄워 메시지를 보내는 것까지는 이 테스트의 목적이 아니다.
 * ObjectMapper의 역직렬화 실패를 인위적으로 재현해 이 경우 정확히 예외를 던지는가만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ShowCreatedEventConsumerTest {

    @Mock
    private SeatGenerationService seatGenerationService;

    private ObjectMapper realObjectMapper;
    private ShowCreatedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new ShowCreatedEventConsumer(seatGenerationService, realObjectMapper);
    }

    @Test
    @DisplayName("정상적인 JSON 메시지는 파싱되어 SeatGenerationService에 위임된다")
    void validMessageDelegatesToSeatGenerationService() {
        String validMessage = """
                {
                  "payload": {
                    "showId": "show-1",
                    "showDate": "2026-12-25T18:00:00",
                    "grades": [
                      {"grade": "VIP", "capacity": 10, "price": 100000}
                    ]
                  }
                }
                """;

        consumer.consume(validMessage);

        verify(seatGenerationService).createSeats(any());
    }

    @Test
    @DisplayName("깨진 JSON(malformed) 메시지는 IllegalStateException을 던지고 서비스는 호출하지 않는다")
    void malformedMessageThrowsAndDoesNotCallService() {
        String malformedMessage = "{ this is not valid json !!!";

        assertThrows(IllegalStateException.class, () -> consumer.consume(malformedMessage));

        verify(seatGenerationService, never()).createSeats(any());
    }

    @Test
    @DisplayName("필수 필드(showDate, grades)가 빠진 메시지는 IllegalStateException으로 명확히 거부된다")
    void missingRequiredFieldThrowsIllegalState() {
        String incompleteMessage = """
                { "payload": { "showId": "show-1" } }
                """;

        /**
         * JSON 문법 자체는 유효해서 ObjectMapper 파싱은 성공하지만(malformed 체크 통과),
         * showDate/grades가 null인 의미적으로 불완전한 메시지다.
         * 원래는 이 상태로 SeatGenerationService까지 넘어가 NPE가 났었다.
         * 컨슈머에 필수 필드 검증을 추가해, 이제는 명확한 IllegalStateException으로 걸러지고 SeatGenerationService는 아예 호출되지 않는다.
         */
        assertThrows(IllegalStateException.class, () -> consumer.consume(incompleteMessage));

        verify(seatGenerationService, never()).createSeats(any());
    }
}