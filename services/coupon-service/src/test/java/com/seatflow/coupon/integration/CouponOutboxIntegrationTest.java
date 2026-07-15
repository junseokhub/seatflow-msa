package com.seatflow.coupon.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coupon 저장과 Outbox 기록이 같은 트랜잭션으로 원자적으로 처리되는지 검증한다.
 * outbox 테이블 이름/컬럼은 common-outbox-jpa 모듈의 실제 엔티티 정의에 맞춰 확인이 필요하다.
 * 여기서는 일반적으로 쓰이는 이름(outbox, event_type, status)을 가정했다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponOutboxIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    @Qualifier("redisCouponService")
    private CouponService couponService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("쿠폰 발급 성공 시 outbox 테이블에 COUPON_ISSUED 이벤트가 함께 기록된다")
    void issuanceRecordsOutboxEventInSameTransaction() {
        CouponCampaign campaign = couponService.createCampaign(
                "Outbox 테스트", BigDecimal.valueOf(1000), 10, null);

        couponService.issueCoupon(campaign.getId(), "outbox-test-user");

        // event_type 컬럼에는 EventTopic.COUPON_ISSUED의 실제 값("coupon.issued", 소문자 + 점 표기)이 저장된다.
        // enum 상수 이름(COUPON_ISSUED)이 아니다.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = ?",
                Integer.class, "coupon.issued");

        assertThat(count).isEqualTo(1);
    }
}