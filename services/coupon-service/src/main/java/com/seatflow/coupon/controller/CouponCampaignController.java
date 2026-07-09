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

import java.util.List;

/**
 * 캠페인(선착순 발급 단위) 관리 API. 생성은 관리자만, 조회는 누구나 —
 * 사용자가 지금 어떤 쿠폰 이벤트가 열려 있는지 봐야 발급을 시도할 수 있다.
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<CampaignResponse>>> getCampaigns() {
        return ResponseEntity.ok(ApiResponse.ok(
                couponService.getCampaigns().stream()
                        .map(CampaignResponse::from)
                        .toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                CampaignResponse.from(couponService.getCampaign(id))));
    }
}
