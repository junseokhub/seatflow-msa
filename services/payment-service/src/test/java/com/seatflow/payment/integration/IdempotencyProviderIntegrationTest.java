package com.seatflow.payment.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.payment.redis.IdempotencyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdempotencyProvider의 Lua 스크립트(TRY_ACQUIRE)가 진짜 Redis 위에서 원자적으로
 * 동작하는지 검증한다. "처음/처리중/완료" 세 상태 전이와, 같은 키로 동시에
 * 몰리는 요청 중 정확히 하나만 통과하는지가 핵심이다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyProviderIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private IdempotencyProvider idempotencyProvider;

    @Test
    @DisplayName("처음 요청은 1(진행 허용)을 반환한다")
    void firstRequestReturnsOne() {
        long result = idempotencyProvider.tryAcquire("key-1");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 PROCESSING 상태인 키에 재요청하면 0(거절)을 반환한다")
    void processingKeyReturnsZeroOnRetry() {
        idempotencyProvider.tryAcquire("key-2");   // 첫 요청, PROCESSING 상태로 만듦

        long result = idempotencyProvider.tryAcquire("key-2");   // 재요청

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("markDone() 이후 같은 키로 재요청하면 -1(이미 완료)을 반환한다")
    void doneKeyReturnsMinusOneOnRetry() {
        idempotencyProvider.tryAcquire("key-3");
        idempotencyProvider.markDone("key-3");

        long result = idempotencyProvider.tryAcquire("key-3");

        assertThat(result).isEqualTo(-1L);
    }

    @Test
    @DisplayName("release() 이후 같은 키로 재요청하면 다시 1(진행 허용)을 반환한다")
    void releasedKeyAllowsRetry() {
        idempotencyProvider.tryAcquire("key-4");
        idempotencyProvider.release("key-4");

        long result = idempotencyProvider.tryAcquire("key-4");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("release()는 키를 완전히 삭제한다 (다음 요청이 최초 요청과 동일하게 취급됨)")
    void releaseDeletesKeyEntirely() {
        idempotencyProvider.tryAcquire("key-5");
        idempotencyProvider.markDone("key-5");   // DONE 상태까지 만들어서 release 전후 차이를 명확히 함

        idempotencyProvider.release("key-5");

        // release 전이었다면 markDone 때문에 -1(이미완료)이 나와야 하지만,
        // release로 키 자체가 지워졌으므로 완전히 새 요청처럼 1이 나온다.
        long result = idempotencyProvider.tryAcquire("key-5");
        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 키에 진짜로 동시에 여러 요청이 몰려도 정확히 하나만 1을 받는다")
    void concurrentRequestsOnSameKeyOnlyOneSucceeds() throws InterruptedException {
        int requestCount = 20;
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
                    if (idempotencyProvider.tryAcquire("concurrent-key") == 1L) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
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
    }
}