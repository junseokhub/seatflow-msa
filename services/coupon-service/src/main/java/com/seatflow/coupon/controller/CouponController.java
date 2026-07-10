package com.seatflow.coupon.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.coupon.dto.CouponResponse;
import com.seatflow.coupon.service.CouponService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(@Qualifier("redisCouponService") CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/campaigns/{campaignId}/issue")
    public ResponseEntity<ApiResponse<CouponResponse>> issueCoupon(
            @PathVariable Long campaignId,
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.ok(
                CouponResponse.from(couponService.issueCoupon(campaignId, userId))));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getMyCoupons(
            Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(ApiResponse.ok(
                couponService.getUserCoupons(userId).stream()
                        .map(CouponResponse::from)
                        .toList()));
    }
}