package com.seatflow.coupon.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.domain.CouponStatus;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * DefaultMysqlCouponService(원자적 UPDATE 방식)의 분기 로직을 검증한다.
 * RedisCouponServiceTest와 같은 구조로 짜서 두 구현체를 나란히 비교할 수 있게 했다.
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
        @DisplayName("원자적 UPDATE가 0건 갱신되면(재고소진) CAMPAIGN_SOLD_OUT 예외, insert는 안 한다")
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

            // RedisCouponService와 핵심 차이
            // 여기는 재고 복원 메서드 자체가 없다. 이미 차감된 재고는 정당하게 이 요청이 차지한 몫이라는 설계 원칙 때문이다.
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

        @Test
        @DisplayName("존재하지 않는 캠페인이면 CAMPAIGN_NOT_FOUND 예외")
        void throwsNotFoundForUnknownCampaign() {
            given(campaignRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.issueCoupon(999L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.CAMPAIGN_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("createCampaign()")
    class CreateCampaign {

        @Test
        @DisplayName("캠페인을 저장만 한다 (Redis 재고 초기화 없음 - MySQL 전용 구현이므로)")
        void savesCampaignWithoutRedis() {
            given(campaignRepository.save(any(CouponCampaign.class))).willReturn(activeCampaign);

            CouponCampaign result = couponService.createCampaign(
                    "캠페인", BigDecimal.valueOf(5000), 100, null);

            assertThat(result).isEqualTo(activeCampaign);
        }
    }

    @Nested
    @DisplayName("조회 메서드")
    class Queries {

        @Test
        @DisplayName("getCampaigns()는 전체 캠페인 목록을 그대로 반환한다")
        void getCampaignsReturnsAll() {
            given(campaignRepository.findAll()).willReturn(List.of(activeCampaign));

            List<CouponCampaign> result = couponService.getCampaigns();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getCampaign()은 존재하는 캠페인을 반환한다")
        void getCampaignReturnsExisting() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));

            CouponCampaign result = couponService.getCampaign(1L);

            assertThat(result).isEqualTo(activeCampaign);
        }

        @Test
        @DisplayName("getCampaign()은 없는 캠페인이면 CAMPAIGN_NOT_FOUND 예외를 던진다")
        void getCampaignThrowsWhenNotFound() {
            given(campaignRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.getCampaign(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.CAMPAIGN_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("getUserCoupons()는 해당 유저의 쿠폰 목록을 반환한다")
        void getUserCouponsReturnsList() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(1000)).build();
            given(couponRepository.findByUserId("user1")).willReturn(List.of(coupon));

            List<Coupon> result = couponService.getUserCoupons("user1");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getCoupon()은 본인 쿠폰이면 정상 반환한다")
        void getCouponReturnsOwnedCoupon() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(1000)).build();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            Coupon result = couponService.getCoupon(1L, "user1");

            assertThat(result.getUserId()).isEqualTo("user1");
        }

        @Test
        @DisplayName("getCoupon()은 본인 쿠폰이 아니면 COUPON_NOT_OWNED 예외를 던진다")
        void getCouponThrowsWhenNotOwned() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("owner").discountAmount(BigDecimal.valueOf(1000)).build();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            assertThatThrownBy(() -> couponService.getCoupon(1L, "someone-else"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.COUPON_NOT_OWNED.getMessage());
        }

        @Test
        @DisplayName("getCoupon()은 존재하지 않으면 COUPON_NOT_FOUND 예외를 던진다")
        void getCouponThrowsWhenNotFound() {
            given(couponRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.getCoupon(999L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.COUPON_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("validateForReservation()")
    class ValidateForReservation {

        @Test
        @DisplayName("ISSUED 상태의 본인 쿠폰이면 할인액을 반환한다")
        void returnsDiscountAmountForIssuedCoupon() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(3000)).build();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            BigDecimal result = couponService.validateForReservation(1L, "user1");

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("RESTORED 상태의 쿠폰도 검증을 통과한다")
        void restoredCouponPassesValidation() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(3000)).build();
            coupon.reserve(100L);
            coupon.restore();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            BigDecimal result = couponService.validateForReservation(1L, "user1");

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }

        @Test
        @DisplayName("이미 USED인 쿠폰은 COUPON_NOT_USABLE 예외를 던진다")
        void throwsWhenCouponAlreadyUsed() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(3000)).build();
            coupon.reserve(100L);
            coupon.confirmUse();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            assertThatThrownBy(() -> couponService.validateForReservation(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.COUPON_NOT_USABLE.getMessage());
        }
    }

    @Nested
    @DisplayName("confirmForReservation()")
    class ConfirmForReservation {

        @Test
        @DisplayName("ISSUED 상태의 본인 쿠폰을 정상적으로 RESERVED로 확정한다")
        void confirmsIssuedCoupon() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(3000)).build();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            couponService.confirmForReservation(1L, "user1", 100L);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED);
            assertThat(coupon.getReservationId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("이미 USED인 쿠폰을 확정하려 하면 COUPON_NOT_USABLE 예외를 던진다")
        void throwsWhenCouponNotUsable() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(3000)).build();
            coupon.reserve(100L);
            coupon.confirmUse();
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));

            assertThatThrownBy(() -> couponService.confirmForReservation(1L, "user1", 200L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.COUPON_NOT_USABLE.getMessage());
        }
    }

    @Nested
    @DisplayName("confirmUse()")
    class ConfirmUse {

        @Test
        @DisplayName("reservationId로 쿠폰을 찾아 USED로 확정한다")
        void confirmsUseByReservationId() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(3000)).build();
            coupon.reserve(100L);
            given(couponRepository.findByReservationId(100L)).willReturn(Optional.of(coupon));

            couponService.confirmUse(100L);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @Test
        @DisplayName("쿠폰을 안 쓴 예매면 아무 일도 안 하고 조용히 끝난다")
        void doesNothingWhenNoCouponForReservation() {
            given(couponRepository.findByReservationId(999L)).willReturn(Optional.empty());

            couponService.confirmUse(999L);
        }
    }

    @Nested
    @DisplayName("restoreByReservation()")
    class RestoreByReservation {

        @Test
        @DisplayName("reservationId로 쿠폰을 찾아 RESTORED로 복원한다")
        void restoresCouponByReservationId() {
            Coupon coupon = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(3000)).build();
            coupon.reserve(100L);
            given(couponRepository.findByReservationId(100L)).willReturn(Optional.of(coupon));

            couponService.restoreByReservation(100L);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESTORED);
            assertThat(coupon.getReservationId()).isNull();
        }

        @Test
        @DisplayName("쿠폰을 안 쓴 예매의 취소면 아무 일도 안 하고 조용히 끝난다")
        void doesNothingWhenNoCouponForReservation() {
            given(couponRepository.findByReservationId(999L)).willReturn(Optional.empty());

            couponService.restoreByReservation(999L);
        }
    }
}