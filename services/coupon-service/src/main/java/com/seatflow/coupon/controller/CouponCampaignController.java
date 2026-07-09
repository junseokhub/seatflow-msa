package com.seatflow.coupon.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.coupon.dto.CampaignResponse;
import com.seatflow.coupon.dto.CreateCampaignRequest;
import com.seatflow.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 캠페인(선착순 발급 단위) 관리 API. 생성은 관리자만 할 수 있다.
 */
@RestController
@RequestMapping("/api/coupons/campaigns")
@RequiredArgsConstructor
public class CouponCampaignController {

    private final CouponService couponService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(
            @RequestBody @Valid CreateCampaignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(CampaignResponse.from(
                couponService.createCampaign(
                        request.name(), request.discountAmount(),
                        request.totalQuantity(), request.expiresAt())
        )));
    }
}