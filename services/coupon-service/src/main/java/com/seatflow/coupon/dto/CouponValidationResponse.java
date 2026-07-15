package com.seatflow.coupon.dto;

import java.math.BigDecimal;

/**
 * reservation-service가 예매 시점에 쿠폰 유효성을 동기 조회할 때 받는 응답.
 * 10편에서 결제 금액을 서버가 재검증했던 것과 같은 이유로, 할인액도 서버(coupon-service) 값을 신뢰의 기준으로 삼는다.
 * 클라이언트가 보낸 할인액을 그대로 믿지 않는다.
 */
public record CouponValidationResponse(
        Long couponId,
        boolean valid,
        BigDecimal discountAmount
) {}