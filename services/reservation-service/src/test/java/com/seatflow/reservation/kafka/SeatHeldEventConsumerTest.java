package com.seatflow.reservation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import com.seatflow.reservation.service.ReservationService;
import com.seatflow.reservation.service.command.CreateReservationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatHeldEventConsumerTest {

    @Mock
    private ReservationService reservationService;

    private SeatHeldEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new SeatHeldEventConsumer(reservationService, realObjectMapper);
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 createReservation에 정확한 값으로 위임된다")
    void validMessageDelegatesToCreateReservation() {
        String validMessage = """
                {
                  "payload": {
                    "userId": "user1",
                    "showId": "show-1",
                    "seatId": 3,
                    "price": 50000,
                    "showDate": "2026-12-25T19:00:00"
                  }
                }
                """;
        Reservation dummy = Reservation.builder()
                .userId("user1").showId("show-1").seatId(3L)
                .amount(BigDecimal.valueOf(50000))
                .showDate(java.time.LocalDateTime.of(2026, 12, 25, 19, 0))
                .status(ReservationStatus.PENDING)
                .build();
        given(reservationService.createReservation(any())).willReturn(dummy);

        consumer.consume(validMessage);

        ArgumentCaptor<CreateReservationCommand> captor = ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(reservationService).createReservation(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("user1");
        assertThat(captor.getValue().seatId()).isEqualTo(3L);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("깨진 메시지는 IllegalStateException을 던지고(DLQ 대상) 서비스는 호출하지 않는다")
    void malformedMessageThrowsForDlq() {
        assertThrows(IllegalStateException.class, () -> consumer.consume("not json"));

        verify(reservationService, never()).createReservation(any());
    }
}