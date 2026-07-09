package com.seatflow.common.client;

import com.seatflow.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "coupon-service", url = "${seatflow.coupon.url}",
        fallback = CouponClient.CouponClientFallback.class)
public interface CouponClient {

    @PostMapping("/internal/coupons/{couponId}/validate")
    ApiResponse<CouponValidationView> validateCoupon(
            @PathVariable("couponId") Long couponId,
            @RequestParam("userId") String userId);

    @PostMapping("/internal/coupons/{couponId}/confirm")
    ApiResponse<Void> confirmCoupon(
            @PathVariable("couponId") Long couponId,
            @RequestParam("userId") String userId,
            @RequestParam("reservationId") Long reservationId);

    @PostMapping("/internal/coupons/reservations/{reservationId}/restore")
    ApiResponse<Void> restoreCoupon(@PathVariable("reservationId") Long reservationId);

    record CouponValidationView(Long couponId, boolean valid, BigDecimal discountAmount) {}

    class CouponClientFallback implements CouponClient {
        @Override
        public ApiResponse<CouponValidationView> validateCoupon(Long couponId, String userId) {
            return ApiResponse.fail("쿠폰 서비스에 일시적으로 연결할 수 없습니다.");
        }

        @Override
        public ApiResponse<Void> confirmCoupon(Long couponId, String userId, Long reservationId) {
            // 결제(PG)는 이미 성공한 뒤에 호출되는 지점이다. 여기서 실패해도 결제
            // 자체를 되돌리면 안 되므로, 예외를 던지지 않고 실패 응답만 반환한다.
            // 호출부(DefaultPaymentService.confirmCoupon)가 이미 이 실패를 "로그만
            // 남기고 넘어가는" 방식으로 처리하고 있어 fallback과 자연스럽게 맞는다.
            return ApiResponse.fail("쿠폰 확정에 실패했습니다. 수동 확인이 필요합니다.");
        }

        @Override
        public ApiResponse<Void> restoreCoupon(Long reservationId) {
            return ApiResponse.fail("쿠폰 복원에 실패했습니다. 수동 확인이 필요합니다.");
        }
    }
}