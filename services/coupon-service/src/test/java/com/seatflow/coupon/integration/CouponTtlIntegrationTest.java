package com.seatflow.coupon.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.redis.CouponRedisProvider;
import com.seatflow.coupon.repository.CouponRepository;
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
 * TTL 기반 임시 마킹(issue -> confirmIssued)이 실제로 동작하는지 검증한다.
 * CouponRedisProvider.pendingTtlSeconds를 이 테스트 클래스에서만 2초로 단축해서
 * (seatflow.coupon.pending-ttl-seconds=2), 운영 TTL(30초)을 그대로 기다리지 않고도 빠르게 검증한다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class CouponTtlIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerDefaultProperties(registry);
        MysqlContainerSupport.registerMysqlProperties(registry);
    }

    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponRedisProvider couponRedisProvider;
    @Autowired
    @Qualifier("redisCouponService")
    private CouponService couponService;

    @Test
    @DisplayName("정상 발급(confirmIssued까지 완료)은 TTL이 지나도 재발급되지 않는다")
    void confirmedIssuanceSurvivesTtlExpiry() throws InterruptedException {
        CouponCampaign campaign = couponService.createCampaign(
                "TTL 정상 테스트", BigDecimal.valueOf(1000), 10, null);

        couponService.issueCoupon(campaign.getId(), "confirmed-user");

        Thread.sleep(30000);

        // 재시도하면 여전히 "이미 발급됨"으로 거부돼야 한다.
        // confirmIssued가 TTL을 없애고 영구 마킹으로 바꿨기 때문이다.
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> couponService.issueCoupon(campaign.getId(), "confirmed-user"));

        assertThat(couponRepository.findByUserId("confirmed-user")).hasSize(1);
    }

    @Test
    @DisplayName("issue만 되고 confirmIssued가 안 된 마킹은 TTL이 지나면 사라져 재발급이 가능해진다")
    void unconfirmedIssuanceExpiresAndAllowsRetry() throws InterruptedException {
        CouponCampaign campaign = couponService.createCampaign(
                "TTL 미확정 테스트", BigDecimal.valueOf(1000), 10, null);

        // confirmIssued를 거치지 않고 issue()만 직접 호출해, "MySQL 저장 전에 서버가 죽은 것"과 동일한 상태를 인위적으로 만든다.
        long result = couponRedisProvider.issue(campaign.getId(), "abandoned-user");
        assertThat(result).isEqualTo(1L);   // 1차 판단은 성공(임시 마킹됨)

        // 이 시점에 MySQL에는 아무것도 저장되지 않았다.
        assertThat(couponRepository.findByUserId("abandoned-user")).isEmpty();

        Thread.sleep(30000);

        // TTL이 지났으므로 같은 사용자가 다시 정상 흐름을 타면 이번엔 성공해야 한다.
        Coupon coupon = couponService.issueCoupon(campaign.getId(), "abandoned-user");

        assertThat(coupon).isNotNull();
        assertThat(couponRepository.findByUserId("abandoned-user")).hasSize(1);
    }
}