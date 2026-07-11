package com.seatflow.coupon.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.domain.CouponStatus;
import com.seatflow.coupon.exception.CouponErrorCode;
import com.seatflow.coupon.redis.CouponRedisProvider;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultRedisCouponServiceTest {

    @Mock
    private CouponCampaignRepository campaignRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedisProvider couponRedisProvider;
    @Mock
    private CouponPersister couponPersister;

    private DefaultRedisCouponService couponService;

    private CouponCampaign activeCampaign;

    private OutboxAppender outboxAppender;

    @BeforeEach
    void setUp() {
        couponService = new DefaultRedisCouponService(
                campaignRepository, couponRepository, couponRedisProvider, couponPersister, outboxAppender);

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
        @DisplayName("Redis가 1(성공)을 반환하면 CouponPersister로 저장 후 confirmIssued를 호출하고 쿠폰을 반환한다")
        void issuesCouponWhenRedisSucceeds() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(1L);
            Coupon persisted = Coupon.builder()
                    .campaignId(1L).userId("user1").discountAmount(BigDecimal.valueOf(5000)).build();
            given(couponPersister.persist(eq(1L), eq("user1"), any(BigDecimal.class)))
                    .willReturn(persisted);

            Coupon result = couponService.issueCoupon(1L, "user1");

            assertThat(result.getUserId()).isEqualTo("user1");
            verify(couponPersister).persist(eq(1L), eq("user1"), any(BigDecimal.class));
            verify(couponRedisProvider).confirmIssued(1L, "user1");
        }

        @Test
        @DisplayName("Redis가 0(재고소진)을 반환하면 CAMPAIGN_SOLD_OUT 예외")
        void throwsSoldOutWhenRedisReturnsZero() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(0L);

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.CAMPAIGN_SOLD_OUT.getMessage());

            verify(couponPersister, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("Redis가 -1(이미 발급됨)을 반환하면 ALREADY_ISSUED 예외")
        void throwsAlreadyIssuedWhenRedisReturnsMinusOne() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(-1L);

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.ALREADY_ISSUED.getMessage());
        }

        @Test
        @DisplayName("Redis 호출 자체가 예외를 던지면 REDIS_UNAVAILABLE로 Fail-closed 응답한다")
        void failsClosedWhenRedisThrows() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1"))
                    .willThrow(new RuntimeException("Redis connection refused"));

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.REDIS_UNAVAILABLE.getMessage());

            verify(couponPersister, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("캠페인이 만료됐으면 Redis를 아예 호출하지 않고 CAMPAIGN_EXPIRED 예외")
        void throwsExpiredWithoutCallingRedis() {
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

            verify(couponRedisProvider, never()).issue(anyLong(), anyString());
        }

        @Test
        @DisplayName("Redis는 성공했는데 CouponPersister 저장이 실패하면 재고를 복원하고 ALREADY_ISSUED를 던진다")
        void restoresStockWhenPersistFailsDespiteRedisSuccess() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(1L);
            given(couponPersister.persist(eq(1L), eq("user1"), any(BigDecimal.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.ALREADY_ISSUED.getMessage());

            verify(couponRedisProvider).restoreStock(1L, "user1");
            verify(couponRedisProvider, never()).confirmIssued(anyLong(), anyString());
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
        @DisplayName("캠페인 생성 시 Redis 재고를 초기화한다")
        void initializesRedisStockOnCreate() {
            given(campaignRepository.save(any(CouponCampaign.class))).willReturn(activeCampaign);

            couponService.createCampaign("캠페인", BigDecimal.valueOf(5000), 100, null);

            verify(couponRedisProvider).initializeStock(any(), eq(100));
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
        @DisplayName("쿠폰을 안 쓴 예매(reservationId로 못 찾음)면 아무 일도 안 하고 조용히 끝난다")
        void doesNothingWhenNoCouponForReservation() {
            given(couponRepository.findByReservationId(999L)).willReturn(Optional.empty());

            couponService.confirmUse(999L);
            // 예외 없이 끝나면 성공 — 별도 verify 불필요, 그냥 조용히 넘어가는 게 정상 동작이다.
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