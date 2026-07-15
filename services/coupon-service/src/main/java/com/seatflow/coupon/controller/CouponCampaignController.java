package com.seatflow.coupon.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.coupon.dto.CampaignResponse;
import com.seatflow.coupon.dto.CreateCampaignRequest;
import com.seatflow.coupon.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 캠페인(선착순 발급 단위) 관리 API. 생성은 관리자만,
 * 조회는 누구나 사용자가 지금 어떤 쿠폰 이벤트가 열려 있는지 봐야 발급을 시도할 수 있다.
 *
 * CouponService는 두 구현체(RedisCouponService, MysqlCouponService)가 있다.
 * 운영에서는 선착순 트래픽을 Redis가 흡수하는 RedisCouponService를 쓴다
 * MysqlCouponService는 'MySQL만으로 풀면 이렇게 짠다'는 비교 학습 기록으로 남겨둔 것이고 실제로 주입되지 않는다.
 *
 * @Qualifier는 필드가 아니라 생성자 파라미터에 붙여야 확실히 적용된다.
 * @RequiredArgsConstructor(필드에 애노테이션을 붙이는 방식)는 Lombok이 파라미터로 복사하는 과정에서 누락될 수 있어(-parameters 컴파일 옵션 여부에 따라 달라짐),
 * 이 클래스는 생성자를 직접 선언한다.
 */
@RestController
@RequestMapping("/api/coupons/campaigns")
public class CouponCampaignController {

    private final CouponService couponService;

    public CouponCampaignController(@Qualifier("redisCouponService") CouponService couponService) {
        this.couponService = couponService;
    }

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