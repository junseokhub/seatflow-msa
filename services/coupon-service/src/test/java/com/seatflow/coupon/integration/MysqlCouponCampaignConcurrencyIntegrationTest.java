package com.seatflow.coupon.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.repository.CouponCampaignRepository;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MysqlCouponService(9편 방식, 원자적 UPDATE)의 선착순 발급이 진짜 동시 요청
 * 상황에서 안전한지 검증한다.
 *
 * CouponCampaignRepository.increaseIssuedQuantity()를 테스트에서 직접 호출하지
 * 않는다 — Repository 메서드 자체에는 트랜잭션을 걸지 않는 게 원칙이고(트랜잭션
 * 경계는 서비스 계층의 책임), Repository를 트랜잭션 없이 직접 호출하면
 * InvalidDataAccessApiUsageException이 난다(직접 겪었다). 대신 이미 @Transactional이
 * 걸려 있는 MysqlCouponService.issueCoupon()을 그대로 호출한다 — 이게 실제
 * 운영에서 벌어지는 흐름 그대로이기도 해서, Repository를 우회하는 것보다 오히려
 * 더 정확한 검증이다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MysqlCouponCampaignConcurrencyIntegrationTest implements MysqlContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private CouponCampaignRepository campaignRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    @Qualifier("mysqlCouponService")
    private CouponService mysqlCouponService;

    @Test
    @DisplayName("동시에 재고보다 많은 요청이 진짜로 몰려도 원자적 UPDATE가 재고 초과 발급을 막는다")
    void concurrentIssuanceNeverExceedsStock() throws InterruptedException {
        int totalStock = 10;
        int requestCount = 30;

        CouponCampaign campaign = mysqlCouponService.createCampaign(
                "동시성 테스트(MySQL)", BigDecimal.valueOf(1000), totalStock, null);

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            String userId = "user" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    mysqlCouponService.issueCoupon(campaign.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(totalStock);
        assertThat(failCount.get()).isEqualTo(requestCount - totalStock);

        long actualSavedCount = couponRepository.countByCampaignId(campaign.getId());
        assertThat(actualSavedCount).isEqualTo(totalStock);
    }
}