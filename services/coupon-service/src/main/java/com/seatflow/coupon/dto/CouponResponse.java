package com.seatflow.coupon.dto;

import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        Long campaignId,
        BigDecimal discountAmount,
        CouponStatus status,
        LocalDateTime issuedAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCampaignId(),
                coupon.getDiscountAmount(),
                coupon.getStatus(),
                coupon.getIssuedAt()
        );
    }
}