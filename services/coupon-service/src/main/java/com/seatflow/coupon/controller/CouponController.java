package com.seatflow.coupon.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.coupon.dto.CouponResponse;
import com.seatflow.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자가 쿠폰을 선착순으로 발급받고 조회하는 API. 전부 인증된 본인 기준으로만
 * 동작한다(userId는 요청 파라미터가 아니라 Authentication에서 가져온다 — 다른
 * 사용자 명의로 발급받는 걸 막기 위해서다).
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

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