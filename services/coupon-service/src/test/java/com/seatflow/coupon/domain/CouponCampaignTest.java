package com.seatflow.coupon.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponCampaignTest {

    @Nested
    @DisplayName("isExpired()")
    class IsExpired {

        @Test
        @DisplayName("만료 시간이 없으면(null) 무기한이라 만료되지 않는다")
        void noExpiryMeansNeverExpired() {
            CouponCampaign campaign = CouponCampaign.builder()
                    .name("무기한 캠페인")
                    .discountAmount(BigDecimal.valueOf(1000))
                    .totalQuantity(10)
                    .expiresAt(null)
                    .build();

            assertThat(campaign.isExpired(LocalDateTime.now().plusYears(10))).isFalse();
        }

        @Test
        @DisplayName("현재 시각이 만료 시각 이후면 만료된 것으로 판단한다")
        void expiredWhenNowIsAfterExpiresAt() {
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
            CouponCampaign campaign = CouponCampaign.builder()
                    .name("한정 캠페인")
                    .discountAmount(BigDecimal.valueOf(1000))
                    .totalQuantity(10)
                    .expiresAt(expiresAt)
                    .build();

            assertThat(campaign.isExpired(expiresAt.plusMinutes(1))).isTrue();
        }

        @Test
        @DisplayName("현재 시각이 만료 시각 이전이면 아직 만료되지 않은 것으로 판단한다")
        void notExpiredWhenNowIsBeforeExpiresAt() {
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
            CouponCampaign campaign = CouponCampaign.builder()
                    .name("한정 캠페인")
                    .discountAmount(BigDecimal.valueOf(1000))
                    .totalQuantity(10)
                    .expiresAt(expiresAt)
                    .build();

            assertThat(campaign.isExpired(expiresAt.minusMinutes(1))).isFalse();
        }
    }

    @Nested
    @DisplayName("isSoldOut()")
    class IsSoldOut {

        @Test
        @DisplayName("발급 수량이 총 수량에 도달하지 않으면 매진이 아니다")
        void notSoldOutWhenIssuedLessThanTotal() {
            CouponCampaign campaign = CouponCampaign.builder()
                    .name("캠페인")
                    .discountAmount(BigDecimal.valueOf(1000))
                    .totalQuantity(10)
                    .expiresAt(null)
                    .build();

            assertThat(campaign.isSoldOut()).isFalse();
        }

        /* 참고: issuedQuantity를 총 수량만큼 늘리는 공개 API가 도메인에 없다.
         * (원자적 UPDATE는 Repository 레벨에서 처리되고, 엔티티는 그 결과를 그대로 읽기만 한다).
         * 그래서 "매진 상태"를 직접 검증하려면 Reflection이나 Repository를 통한 통합 테스트가 필요하다.
         * 이건 통합 테스트 쪽에서 확인한다.
         * MysqlCouponService를 쓰는 경로에서만 issuedQuantity가 의미를 가지므로(RedisCouponService는 재고 판단을 Redis가 하고,
         * CouponCampaign.issuedQuantity는 안 씀,
         * 이 필드 자체가 지금은 MysqlCouponService 전용이라는 점도 참고할 것.
         */
    }
}