package com.seatflow.coupon.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.exception.CouponErrorCode;
import com.seatflow.coupon.service.CouponService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis 장애 시 Fail-closed 동작을 검증한다.
 * 다른 테스트들이 공유하는 Singleton Redis 컨테이너(RedisContainerSupport)를 이 테스트 때문에 멈추면 이후 모든 테스트가 깨지므로,
 * 이 클래스만 독립적인 전용 Redis 컨테이너를 새로 띄운다.
 * 여기서만 stop()으로 장애를 강제할 수 있게
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponRedisFailureIntegrationTest implements MysqlContainerSupport {


    private static final GenericContainer<?> DEDICATED_REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        DEDICATED_REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
        registry.add("spring.data.redis.host", DEDICATED_REDIS::getHost);
        registry.add("spring.data.redis.port", () -> DEDICATED_REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void tearDown() {
        DEDICATED_REDIS.stop();   // 이 테스트 클래스 전용이라 마음대로 정리해도 안전하다.
    }

    @Autowired
    @Qualifier("redisCouponService")
    private CouponService couponService;

    @Test
    @DisplayName("Redis가 죽어있으면 발급 요청이 REDIS_UNAVAILABLE로 즉시 실패한다 (Fail-closed)")
    void issuanceFailsClosedWhenRedisIsDown() {
        CouponCampaign campaign = couponService.createCampaign(
                "Redis 장애 테스트", BigDecimal.valueOf(1000), 10, null);

        // 캠페인 생성(initializeStock)까지는 Redis가 살아있을 때 정상 처리됐다.
        // 이제 Redis를 강제로 죽여서, 그 이후의 발급 요청이 어떻게 되는지 본다.
        DEDICATED_REDIS.stop();

        assertThatThrownBy(() -> couponService.issueCoupon(campaign.getId(), "user1"))
                .hasMessageContaining(CouponErrorCode.REDIS_UNAVAILABLE.getMessage());

        // MySQL에는 아무것도 저장되지 않아야 한다 — Fail-closed는 Redis 장애 시
        // MySQL 경로로 넘어가지 않는다는 뜻이다.
    }
}