package com.seatflow.common.event.payment;

import java.math.BigDecimal;

/**
 * 취소 Saga의 환불 명령. reservation(오케스트레이터)이 계산한 환불액(수수료 반영)을 실어
 * payment에 내린다. payment는 결제한 수단(PaymentStrategy)으로 환불을 라우팅한다.
 */
public record PaymentRefundCommand(
        Long sagaId,
        Long reservationId,
        BigDecimal refundAmount
) {}