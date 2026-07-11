package com.seatflow.seat.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.seat.redis.SeatRedisProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeatRedisProvider의 Lua 스크립트(HOLD_ALL, RELEASE_IF_OWNER)가 진짜 Redis 위에서
 * 원자적으로 동작하는지 검증한다. 특히 holdAll의 "전부 아니면 전무" 특성과, 여러
 * 좌석 조합을 두고 여러 사용자가 동시에 경쟁할 때 정확히 한 명만 성공하는지를
 * Mock으로는 검증할 수 없다 — 진짜 동시 요청이 필요하다.
 *
 * 이 테스트가 검증하려는 건 Redis뿐이지만, seat-service의 전체 스프링 컨텍스트가
 * JPA/DataSource 자동 설정을 갖고 있어 MySQL 연결 정보 없이는 컨텍스트 자체가
 * 뜨지 않는다 — MysqlContainerSupport를 같이 포함시켜 컨텍스트 로딩만 성립시킨다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeatRedisProviderIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private SeatRedisProvider seatRedisProvider;

    @Test
    @DisplayName("holdAll()은 여러 좌석 중 하나라도 이미 점유돼 있으면 전체가 실패한다 (전부 아니면 전무)")
    void holdAllFailsIfAnySeatAlreadyHeld() {
        seatRedisProvider.hold("show-1", 1L, "other-user");   // 좌석 1을 미리 점유

        boolean acquired = seatRedisProvider.holdAll("show-1", List.of(1L, 2L, 3L), "user1");

        assertThat(acquired).isFalse();
        // 실패했으므로 2, 3번 좌석도 점유되지 않아야 한다 (부분 성공 없음).
        assertThat(seatRedisProvider.isHeld("show-1", 2L)).isFalse();
        assertThat(seatRedisProvider.isHeld("show-1", 3L)).isFalse();
    }

    @Test
    @DisplayName("holdAll()은 전부 비어있으면 모든 좌석을 한 번에 점유한다")
    void holdAllSucceedsWhenAllSeatsAreFree() {
        boolean acquired = seatRedisProvider.holdAll("show-1", List.of(4L, 5L, 6L), "user1");

        assertThat(acquired).isTrue();
        assertThat(seatRedisProvider.isHeld("show-1", 4L)).isTrue();
        assertThat(seatRedisProvider.isHeld("show-1", 5L)).isTrue();
        assertThat(seatRedisProvider.isHeld("show-1", 6L)).isTrue();
    }

    @Test
    @DisplayName("releaseIfOwner()는 본인 소유일 때만 해제하고 1을 반환한다")
    void releaseIfOwnerReleasesOnlyWhenOwned() {
        seatRedisProvider.hold("show-1", 7L, "user1");

        long result = seatRedisProvider.releaseIfOwner("show-1", 7L, "user1");

        assertThat(result).isEqualTo(1L);
        assertThat(seatRedisProvider.isHeld("show-1", 7L)).isFalse();
    }

    @Test
    @DisplayName("releaseIfOwner()는 타인 소유면 0을 반환하고 해제하지 않는다")
    void releaseIfOwnerReturnsZeroWhenNotOwner() {
        seatRedisProvider.hold("show-1", 8L, "owner-user");

        long result = seatRedisProvider.releaseIfOwner("show-1", 8L, "other-user");

        assertThat(result).isEqualTo(0L);
        assertThat(seatRedisProvider.isHeld("show-1", 8L)).isTrue();   // 그대로 유지
    }

    @Test
    @DisplayName("releaseIfOwner()는 애초에 점유가 없으면 -1을 반환한다")
    void releaseIfOwnerReturnsMinusOneWhenNotHeld() {
        long result = seatRedisProvider.releaseIfOwner("show-1", 9999L, "user1");

        assertThat(result).isEqualTo(-1L);
    }

    @Test
    @DisplayName("여러 사용자가 같은 좌석 조합에 진짜 동시에 hold를 시도해도 정확히 한 명만 성공한다")
    void concurrentHoldAllOnSameSeatsOnlyOneSucceeds() throws InterruptedException {
        List<Long> seatIds = List.of(10L, 11L, 12L);
        int userCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch readyLatch = new CountDownLatch(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < userCount; i++) {
            String userId = "concurrent-user" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    if (seatRedisProvider.holdAll("show-2", seatIds, userId)) {
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

        assertThat(successCount.get()).isEqualTo(1);   // 딱 한 명만 성공
    }

    @Test
    @DisplayName("getHolder()는 점유자의 userId를 정확히 반환한다")
    void getHolderReturnsCorrectOwner() {
        seatRedisProvider.hold("show-1", 20L, "owner-user");

        String holder = seatRedisProvider.getHolder("show-1", 20L);

        assertThat(holder).isEqualTo("owner-user");
    }

    @Test
    @DisplayName("getHolder()는 점유가 없으면 null을 반환한다")
    void getHolderReturnsNullWhenNotHeld() {
        String holder = seatRedisProvider.getHolder("show-1", 9998L);

        assertThat(holder).isNull();
    }

    @Test
    @DisplayName("release()는 hold()가 잡은 키를 정확히 지운다 (hold/release가 같은 키를 봐야 함)")
    void releaseRemovesTheSameKeyThatHoldSet() {
        seatRedisProvider.hold("show-1", 21L, "user1");
        assertThat(seatRedisProvider.isHeld("show-1", 21L)).isTrue();

        seatRedisProvider.release("show-1", 21L);

        assertThat(seatRedisProvider.isHeld("show-1", 21L)).isFalse();
        // hold()로 잡은 걸 release()가 정확히 지웠다는 건, 이제 getHolder()로도
        // 확인 가능해야 한다 — 셋이 전부 같은 키를 공유한다는 걸 교차 검증한다.
        assertThat(seatRedisProvider.getHolder("show-1", 21L)).isNull();
    }

    @Test
    @DisplayName("hold()로 잡은 좌석을 releaseIfOwner()로도 해제할 수 있다 (서로 다른 메서드가 같은 키를 공유)")
    void holdAndReleaseIfOwnerShareTheSameKey() {
        seatRedisProvider.hold("show-1", 22L, "user1");

        long result = seatRedisProvider.releaseIfOwner("show-1", 22L, "user1");

        assertThat(result).isEqualTo(1L);
        assertThat(seatRedisProvider.isHeld("show-1", 22L)).isFalse();
    }
}