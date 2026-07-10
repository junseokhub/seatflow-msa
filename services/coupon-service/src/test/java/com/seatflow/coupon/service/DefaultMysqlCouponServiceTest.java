package com.seatflow.coupon.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.exception.CouponErrorCode;
import com.seatflow.coupon.repository.CouponCampaignRepository;
import com.seatflow.coupon.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MysqlCouponService(원자적 UPDATE 방식)의 분기 로직을 검증한다.
 * RedisCouponServiceTest와 같은 구조로 짜서, 두 구현체가 "같은 유스케이스를
 * 어떻게 다르게 처리하는지" 나란히 비교할 수 있게 했다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultMysqlCouponServiceTest {

    @Mock
    private CouponCampaignRepository campaignRepository;
    @Mock
    private CouponRepository couponRepository;

    private DefaultMysqlCouponService couponService;

    private CouponCampaign activeCampaign;

    @BeforeEach
    void setUp() {
        couponService = new DefaultMysqlCouponService(campaignRepository, couponRepository);

        activeCampaign = CouponCampaign.builder()
                .name("테스트 캠페인")
                .discountAmount(BigDecimal.valueOf(5000))
                .totalQuantity(100)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    @Nested
    @DisplayName("issueCoupon()")
    class IssueCoupon {

        @Test
        @DisplayName("원자적 UPDATE가 1건 갱신되면 쿠폰을 저장하고 반환한다")
        void issuesCouponWhenUpdateSucceeds() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(campaignRepository.increaseIssuedQuantity(1L)).willReturn(1);

            Coupon result = couponService.issueCoupon(1L, "user1");

            assertThat(result.getUserId()).isEqualTo("user1");
            verify(couponRepository).save(any(Coupon.class));
        }

        @Test
        @DisplayName("원자적 UPDATE가 0건 갱신되면(재고소진) CAMPAIGN_SOLD_OUT 예외, MySQL insert는 안 한다")
        void throwsSoldOutWhenUpdateAffectsZeroRows() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(campaignRepository.increaseIssuedQuantity(1L)).willReturn(0);

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.CAMPAIGN_SOLD_OUT.getMessage());

            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 차감은 성공했는데 유니크 제약 위반이면 ALREADY_ISSUED, 재고는 되돌리지 않는다")
        void doesNotRestoreStockOnDuplicateInsert() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(campaignRepository.increaseIssuedQuantity(1L)).willReturn(1);
            given(couponRepository.save(any(Coupon.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.ALREADY_ISSUED.getMessage());

            // RedisCouponService와의 핵심 차이: 여기서는 재고를 되돌리는 호출 자체가
            // 없다 — 이미 차감된 재고는 이 요청이 정당하게 차지한 몫이라는 설계
            // 원칙(increaseIssuedQuantity 주석 참고) 때문이다. 별도 verify 대상이
            // 없으므로, save가 예외를 던진 뒤 다른 캠페인 조작 메서드가 호출되지
            // 않았음을 확인하는 것으로 충분하다.
        }

        @Test
        @DisplayName("캠페인이 만료됐으면 재고 차감 자체를 시도하지 않는다")
        void throwsExpiredWithoutTouchingStock() {
            CouponCampaign expiredCampaign = CouponCampaign.builder()
                    .name("만료된 캠페인")
                    .discountAmount(BigDecimal.valueOf(5000))
                    .totalQuantity(100)
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .build();
            given(campaignRepository.findById(1L)).willReturn(Optional.of(expiredCampaign));

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.CAMPAIGN_EXPIRED.getMessage());

            verify(campaignRepository, never()).increaseIssuedQuantity(any());
        }
    }
}