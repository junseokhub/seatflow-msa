package com.seatflow.coupon.dto;

import com.seatflow.coupon.domain.CouponCampaign;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CampaignResponse(
        Long id,
        String name,
        BigDecimal discountAmount,
        int totalQuantity,
        int issuedQuantity,
        LocalDateTime expiresAt
) {
    public static CampaignResponse from(CouponCampaign campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getDiscountAmount(),
                campaign.getTotalQuantity(),
                campaign.getIssuedQuantity(),
                campaign.getExpiresAt()
        );
    }
}