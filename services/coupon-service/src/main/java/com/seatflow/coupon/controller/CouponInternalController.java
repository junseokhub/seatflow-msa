package com.seatflow.coupon.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.coupon.dto.CouponValidationResponse;
import com.seatflow.coupon.service.CouponService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 서비스 간 동기 호출(OpenFeign) 전용 API. payment-service가 결제 처리 흐름 안에서
 * validate(PG 호출 전) ->confirm(PG 성공 후) 순서로 호출한다.
 */
@RestController
@RequestMapping("/internal/coupons")
public class CouponInternalController {

    private final CouponService couponService;

    public CouponInternalController(@Qualifier("redisCouponService") CouponService couponService) {
        this.couponService = couponService;
    }

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