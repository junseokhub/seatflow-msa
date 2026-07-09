package com.seatflow.coupon.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.coupon.dto.CouponValidationResponse;
import com.seatflow.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 서비스 간 동기 호출(OpenFeign) 전용 API. payment-service가 결제 처리 흐름 안에서
 * 두 번 나눠 호출한다.
 *
 * 1. validate — PG 호출 *전*. 유효성만 확인하고 상태는 안 바꾼다. 할인액을 받아
 *    PG에 보낼 최종 금액을 계산하는 데 쓴다.
 * 2. confirm  — PG 성공 *후*. 그제야 쿠폰을 해당 예매에 확정 적용(RESERVED)한다.
 *    PG가 실패했는데 쿠폰만 소진되는 걸 막기 위해 반드시 이 순서를 지킨다.
 */
@RestController
@RequestMapping("/internal/coupons")
@RequiredArgsConstructor
public class CouponInternalController {

    private final CouponService couponService;

    @PostMapping("/{couponId}/validate")
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validate(
            @PathVariable Long couponId,
            @RequestParam String userId) {
        BigDecimal discountAmount = couponService.validateForReservation(couponId, userId);
        return ResponseEntity.ok(ApiResponse.ok(
                new CouponValidationResponse(couponId, true, discountAmount)));
    }

    @PostMapping("/{couponId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(
            @PathVariable Long couponId,
            @RequestParam String userId,
            @RequestParam Long reservationId) {
        couponService.confirmForReservation(couponId, userId, reservationId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/reservations/{reservationId}/restore")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable Long reservationId) {
        couponService.restoreByReservation(reservationId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}