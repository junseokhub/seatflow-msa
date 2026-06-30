package com.seatflow.payment.client;

import java.math.BigDecimal;

/**
 * reservation 조회 응답에서 결제 검증에 필요한 필드만 받는 뷰.
 * status는 문자열로 받아 payment가 reservation의 enum에 직접 의존하지 않게 한다.
 */
public record ReservationView(
        Long id,
        String userId,
        Long seatId,
        BigDecimal amount,
        String status
) {}