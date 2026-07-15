package com.seatflow.coupon.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.coupon.domain.CouponCampaign;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정석 동시 출발 패턴: 스레드 풀 크기를 요청 수와 동일하게 만든다. 그러면 모든
 * 작업이 "즉시 실행 중" 상태가 되고(대기 큐에 남는 작업이 없음), readyLatch로
 * "전원이 준비될 때까지 대기 → 동시 출발"을 걸어도 데드락이 생기지 않는다.
 *
 * (이전 시도에서 스레드 풀을 요청 수보다 작게 주고 readyLatch로 전원 대기를
 * 걸었다가 데드락이 났었다 — 풀에 못 들어간 작업은 시작도 못해 readyLatch를
 * 채울 수 없는데, 이미 실행 중인 작업은 그 카운트가 다 찰 때까지 멈춰있어서
 * 서로 영원히 기다리는 상태가 됐다. 스레드 풀을 요청 수만큼 주면 이 문제 자체가
 * 사라진다 — 모든 작업이 즉시 실행 상태이므로 각자 readyLatch를 채울 수 있다.)
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponIssuanceIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerDefaultProperties(registry);
        MysqlContainerSupport.registerMysqlProperties(registry);
    }

    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    @Qualifier("redisCouponService")
    private CouponService couponService;

    @Test
    @DisplayName("재고보다 많은 동시 요청이 진짜로 동시에 몰려도 재고 수만큼만 발급되고 나머지는 실패한다")
    void concurrentIssuanceNeverExceedsStock() throws InterruptedException {
        int totalStock = 10;
        int requestCount = 50;

        CouponCampaign campaign = couponService.createCampaign(
                "동시성 테스트 캠페인", BigDecimal.valueOf(3000), totalStock, null);

        // 스레드 풀 크기 = 요청 수. 모든 요청이 각자 자기 스레드를 즉시 배정받아
        // "실행 대기(큐잉)"가 아니라 "실행 시작"부터 하므로, readyLatch로 전원이
        // 준비될 때까지 기다렸다가 정확히 동시에 출발시켜도 안전하다.
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            String userId = "user" + i;
            executor.submit(() -> {
                readyLatch.countDown();   // "나 준비됐다"
                try {
                    startLatch.await();   // 전원 준비될 때까지 대기
                    couponService.issueCoupon(campaign.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();       // 50개 스레드 전부 준비될 때까지 대기
        startLatch.countDown();   // 정확히 동시에 출발
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(totalStock);
        assertThat(failCount.get()).isEqualTo(requestCount - totalStock);

        long actualSavedCount = couponRepository.countByCampaignId(campaign.getId());
        assertThat(actualSavedCount).isEqualTo(totalStock);
    }

    @Test
    @DisplayName("같은 사용자가 진짜로 동시에 여러 번 요청해도 단 하나만 성공한다 (1인 1매)")
    void sameUserConcurrentRequestsOnlySucceedOnce() throws InterruptedException {
        CouponCampaign campaign = couponService.createCampaign(
                "1인1매 테스트", BigDecimal.valueOf(1000), 100, null);
        String userId = "duplicate-user";
        int requestCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    couponService.issueCoupon(campaign.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(couponRepository.findByUserId(userId)).hasSize(1);
    }

    @Test
    @DisplayName("정상적으로 발급된 쿠폰은 Redis 확정 후 MySQL에 정확히 저장된다")
    void issuedCouponIsPersistedCorrectly() {
        CouponCampaign campaign = couponService.createCampaign(
                "단건 발급 테스트", BigDecimal.valueOf(1000), 5, null);

        couponService.issueCoupon(campaign.getId(), "single-user");

        var saved = couponRepository.findByUserId("single-user");
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getCampaignId()).isEqualTo(campaign.getId());
        assertThat(saved.get(0).getDiscountAmount()).isEqualByComparingTo(campaign.getDiscountAmount());
    }

    @Test
    @DisplayName("재고 소진 후 요청은 즉시 실패하고, MySQL에 추가로 저장되지 않는다")
    void issuanceFailsCleanlyAfterSoldOut() {
        CouponCampaign campaign = couponService.createCampaign(
                "소진 테스트", BigDecimal.valueOf(1000), 1, null);

        couponService.issueCoupon(campaign.getId(), "first-user");

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> couponService.issueCoupon(campaign.getId(), "second-user"));

        assertThat(couponRepository.countByCampaignId(campaign.getId())).isEqualTo(1);
    }
}