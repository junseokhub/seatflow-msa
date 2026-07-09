package com.seatflow.payment.client;

// payment-service에 추가하는 파일. 10편의 ReservationClient(payment -> reservation)와
// 같은 위치, 같은 방식이다. PG(mock)에 넘기기 전에 할인된 최종 금액을 확정하기 위해
// 결제 처리 흐름 안에서 coupon-service를 동기 호출한다.

import com.seatflow.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "coupon-service", url = "${seatflow.coupon.url}")
public interface CouponClient {

    /** 결제 처리 전 검증만. 할인액을 받아 최종 결제 금액을 계산하는 데 쓴다. */
    @PostMapping("/internal/coupons/{couponId}/validate")
    ApiResponse<CouponValidationView> validateCoupon(
            @PathVariable Long couponId,
            @RequestParam String userId);

    /** 결제(PG 처리) 성공 후 확정. reservationId 대신 결제 자체를 식별자로 쓸 수도 있지만,
     * coupon-service는 "어느 예매에 쓰였는지"만 알면 되므로 reservationId를 그대로 넘긴다. */
    @PostMapping("/internal/coupons/{couponId}/confirm")
    ApiResponse<Void> confirmCoupon(
            @PathVariable Long couponId,
            @RequestParam String userId,
            @RequestParam Long reservationId);

    /** 환불/취소 시 복원. */
    @PostMapping("/internal/coupons/reservations/{reservationId}/restore")
    ApiResponse<Void> restoreCoupon(@PathVariable Long reservationId);

    record CouponValidationView(Long couponId, boolean valid, BigDecimal discountAmount) {}
}