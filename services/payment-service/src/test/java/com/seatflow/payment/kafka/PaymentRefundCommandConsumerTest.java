package com.seatflow.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRefundCommandConsumerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentRefundCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new PaymentRefundCommandConsumer(paymentService, realObjectMapper);
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 executeRefund에 정확한 값으로 위임된다")
    void validMessageDelegatesToExecuteRefund() {
        String validMessage = """
                { "payload": { "sagaId": 1, "reservationId": 100, "refundAmount": 45000 } }
                """;

        consumer.consume(validMessage);

        verify(paymentService).executeRefund(1L, 100L, BigDecimal.valueOf(45000));
    }

    @Test
    @DisplayName("깨진 메시지는 IllegalStateException을 던지고(DLQ 대상) 서비스는 호출하지 않는다")
    void malformedMessageThrowsForDlq() {
        assertThrows(IllegalStateException.class, () -> consumer.consume("not json"));

        verify(paymentService, never()).executeRefund(anyLong(), anyLong(), any());
    }
}