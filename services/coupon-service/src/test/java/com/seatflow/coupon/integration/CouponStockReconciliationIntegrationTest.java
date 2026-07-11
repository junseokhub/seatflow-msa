package com.seatflow.coupon.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.redis.CouponRedisProvider;
import com.seatflow.coupon.repository.CouponCampaignRepository;
import com.seatflow.coupon.repository.CouponRepository;
import com.seatflow.coupon.redis.CouponStockReconciliationScheduler;
import com.seatflow.coupon.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponStockReconciliationScheduler가 Redis 재고 누수를 실제로 찾아 복원하는지
 * 검증한다. 정상 흐름(issueCoupon)이 아니라, "MySQL 저장 전에 서버가 죽은 것"과
 * 같은 상황을 couponRedisProvider.issue()만 직접 호출해 인위적으로 재현한다 —
 * 이러면 Redis는 차감됐는데 MySQL엔 아무 기록도 없는, 정확히 누수 상황이 된다.
 *
 * 스케줄러 자체는 @Scheduled로 자동 실행되게 두지 않고(@EnableScheduling 미적용,
 * 15편 설계 그대로), 테스트에서 reconcile()을 직접 호출해 로직만 검증한다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponStockReconciliationIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponCampaignRepository campaignRepository;
    @Autowired
    private CouponRedisProvider couponRedisProvider;
    @Autowired
    @Qualifier("redisCouponService")
    private CouponService couponService;
    @Autowired
    private CouponStockReconciliationScheduler reconciliationScheduler;

    @Test
    @DisplayName("Redis만 차감되고 MySQL엔 없는 누수 상태를 스케줄러가 발견해 재고를 복원한다")
    void reconcileRestoresLeakedStock() {
        CouponCampaign campaign = couponService.createCampaign(
                "재고 누수 테스트", BigDecimal.valueOf(1000), 10, null);

        // 정상 발급 하나 (Redis+MySQL 둘 다 일치하는 정상 케이스)
        couponService.issueCoupon(campaign.getId(), "normal-user");

        // 누수 상황 인위 재현: issue()만 호출하고 persist는 안 함
        // (서버가 그 사이 죽은 것과 동일한 상태)
        couponRedisProvider.issue(campaign.getId(), "leaked-user-1");
        couponRedisProvider.issue(campaign.getId(), "leaked-user-2");

        // 이 시점 Redis 기준 차감량 = 3 (normal-user 확정 1 + leaked 2)
        // MySQL 기준 실제 발급량 = 1 (normal-user만)
        // → 누수 2개가 있는 상태
        Long remainingBeforeReconcile = couponRedisProvider.getRemaining(campaign.getId());
        assertThat(remainingBeforeReconcile).isEqualTo(7L);   // 10 - 3

        reconciliationScheduler.reconcile();

        // reconcile 로직: leaked = redisIssuedCount(3) - mysqlIssuedCount(1) = 2
        //                remaining에 leaked(2)만큼 되돌림 → 7 + 2 = 9
        // "10개 재고 중 실제로 발급된 건 1개뿐"이라는 진실에 맞게, 남은 재고가
        // 9로 정확히 보정된다.
        Long remainingAfterReconcile = couponRedisProvider.getRemaining(campaign.getId());
        assertThat(remainingAfterReconcile).isEqualTo(9L);
    }

    @Test
    @DisplayName("누수가 없는 정상 상태에서는 reconcile을 실행해도 재고가 그대로 유지된다")
    void reconcileDoesNothingWhenNoLeak() {
        CouponCampaign campaign = couponService.createCampaign(
                "정상 상태 테스트", BigDecimal.valueOf(1000), 10, null);

        couponService.issueCoupon(campaign.getId(), "user1");
        couponService.issueCoupon(campaign.getId(), "user2");

        Long remainingBefore = couponRedisProvider.getRemaining(campaign.getId());
        assertThat(remainingBefore).isEqualTo(8L);

        reconciliationScheduler.reconcile();

        Long remainingAfter = couponRedisProvider.getRemaining(campaign.getId());
        assertThat(remainingAfter).isEqualTo(8L);   // 변화 없음
    }

    @Test
    @DisplayName("Redis에 재고 키 자체가 없는 캠페인은 조용히 건너뛴다 (예외 없이 종료)")
    void reconcileSkipsCampaignWithoutRedisKey() {
        // Redis 재고 초기화를 거치지 않고 캠페인만 MySQL에 직접 저장 —
        // initializeStock 누락 또는 Redis 데이터 유실을 재현한다.
        CouponCampaign campaignWithoutRedisKey = campaignRepository.save(
                CouponCampaign.builder()
                        .name("Redis 키 없는 캠페인")
                        .discountAmount(BigDecimal.valueOf(1000))
                        .totalQuantity(10)
                        .expiresAt(null)
                        .build());

        // 예외 없이 정상 종료되어야 한다 — 이 스케줄러의 책임 범위 밖이라
        // 경고만 남기고 넘어가는 것이 의도된 동작이다.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> reconciliationScheduler.reconcile());
    }

    @Test
    @DisplayName("MySQL 발급 수가 Redis 차감량보다 많은 이례적 상황은 자동 보정하지 않는다")
    void reconcileDoesNotAutoCorrectWhenMysqlExceedsRedis() {
        CouponCampaign campaign = couponService.createCampaign(
                "이례적 상황 테스트", BigDecimal.valueOf(1000), 10, null);

        couponService.issueCoupon(campaign.getId(), "user1");   // Redis 차감 1, MySQL 발급 1

        // Redis를 거치지 않고 MySQL에 쿠폰을 직접 추가 삽입 — "다른 경로로 직접
        // INSERT된" 이례적 상황을 인위적으로 재현한다.
        couponRepository.save(Coupon.builder()
                .campaignId(campaign.getId())
                .userId("bypass-user")
                .discountAmount(BigDecimal.valueOf(1000))
                .build());

        Long remainingBefore = couponRedisProvider.getRemaining(campaign.getId());

        // leaked < 0 인 상황이므로 자동 보정(restoreLeakedStock)이 호출되지
        // 않아야 한다 — remaining이 reconcile 전후로 그대로여야 한다.
        reconciliationScheduler.reconcile();

        Long remainingAfter = couponRedisProvider.getRemaining(campaign.getId());
        assertThat(remainingAfter).isEqualTo(remainingBefore);
    }
}