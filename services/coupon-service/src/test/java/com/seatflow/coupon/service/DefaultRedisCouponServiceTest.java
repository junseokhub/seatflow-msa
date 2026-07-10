package com.seatflow.coupon.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * RedisCouponService의 분기 로직(Redis 결과에 따른 예외/정상 처리)을 Mockito로
 * 검증한다. 실제 Redis/MySQL 없이, "Redis가 이런 결과를 줬을 때 서비스가 올바르게
 * 반응하는가"만 확인한다 — 진짜 동시성 상황(여러 스레드가 실제 Redis/MySQL에
 * 동시에 부딪히는 것)은 통합 테스트의 몫이다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultRedisCouponServiceTest {

    @Mock
    private CouponCampaignRepository campaignRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedisProvider couponRedisProvider;
    @Mock
    private OutboxAppender outboxAppender;

    private DefaultRedisCouponService couponService;

    private CouponCampaign activeCampaign;

    @BeforeEach
    void setUp() {
        couponService = new DefaultRedisCouponService(
                campaignRepository, couponRepository, couponRedisProvider, outboxAppender);

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
        @DisplayName("Redis가 1(성공)을 반환하면 MySQL 저장 후 confirmIssued를 호출하고 쿠폰을 반환한다")
        void issuesCouponWhenRedisSucceeds() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(1L);
            given(couponRepository.save(any(Coupon.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            Coupon result = couponService.issueCoupon(1L, "user1");

            assertThat(result.getUserId()).isEqualTo("user1");
            assertThat(result.getCampaignId()).isEqualTo(1L);
            verify(couponRepository).save(any(Coupon.class));
            verify(couponRedisProvider).confirmIssued(1L, "user1");
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Redis가 0(재고소진)을 반환하면 CAMPAIGN_SOLD_OUT 예외, MySQL은 건드리지 않는다")
        void throwsSoldOutWhenRedisReturnsZero() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(0L);

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.CAMPAIGN_SOLD_OUT.getMessage());

            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("Redis가 -1(이미 발급됨)을 반환하면 ALREADY_ISSUED 예외, MySQL은 건드리지 않는다")
        void throwsAlreadyIssuedWhenRedisReturnsMinusOne() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(-1L);

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.ALREADY_ISSUED.getMessage());

            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("Redis 호출 자체가 예외를 던지면(장애) REDIS_UNAVAILABLE로 Fail-closed 응답한다")
        void failsClosedWhenRedisThrows() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1"))
                    .willThrow(new RuntimeException("Redis connection refused"));

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.REDIS_UNAVAILABLE.getMessage());

            verify(couponRepository, never()).save(any());
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
        @DisplayName("Redis는 성공했는데 MySQL 저장이 유니크 제약 위반으로 실패하면 재고를 복원하고 ALREADY_ISSUED를 던진다")
        void restoresStockWhenMysqlInsertFailsDespiteRedisSuccess() {
            given(campaignRepository.findById(1L)).willReturn(Optional.of(activeCampaign));
            given(couponRedisProvider.issue(1L, "user1")).willReturn(1L);
            given(couponRepository.save(any(Coupon.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> couponService.issueCoupon(1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(CouponErrorCode.ALREADY_ISSUED.getMessage());

            verify(couponRedisProvider).restoreStock(1L, "user1");
            // MySQL이 실패했으므로 Redis 마킹은 절대 영구 확정되면 안 된다.
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
}