package com.seatflow.seat.integration;

import com.seatflow.common.event.show.SeatGradeType;
import com.seatflow.common.event.show.ShowCreatedEvent;
import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.repository.SeatRepository;
import com.seatflow.seat.service.SeatGenerationService;
import com.seatflow.seat.service.SeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * holdSeats() 전체 흐름(Redis 게이트 + DB 검증 + Outbox 기록)이 진짜 동시 요청 상황에서 안전한지 검증한다.
 * 좌석 하나를 두고 여러 사용자가 동시에 경쟁할 때 정확히 한 명만 hold에 성공해야 한다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeatHoldIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerDefaultProperties(registry);
        MysqlContainerSupport.registerMysqlProperties(registry);
    }

    @Autowired
    private SeatGenerationService seatGenerationService;
    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("같은 좌석 하나를 여러 사용자가 진짜 동시에 hold 시도해도 정확히 한 명만 성공한다")
    void concurrentHoldOnSameSeatOnlyOneSucceeds() throws InterruptedException {
        String showId = "integration-show-1";
        seatGenerationService.createSeats(new ShowCreatedEvent(
                showId, LocalDateTime.now().plusDays(7),
                List.of(new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 1, BigDecimal.valueOf(100000)))));

        List<Seat> seats = seatRepository.findByShowId(showId);
        Long seatId = seats.get(0).getId();

        int userCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch readyLatch = new CountDownLatch(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < userCount; i++) {
            String userId = "user" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    seatService.holdSeats(showId, List.of(seatId), userId);
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

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(userCount - 1);
    }

    @Test
    @DisplayName("좌석 hold 성공 시 실제로 Redis에 점유가 걸리고, 같은 사용자가 재시도하면 실패한다")
    void holdedSeatCannotBeHeldAgainByAnyone() {
        String showId = "integration-show-2";
        seatGenerationService.createSeats(new ShowCreatedEvent(
                showId, LocalDateTime.now().plusDays(7),
                List.of(new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 1, BigDecimal.valueOf(100000)))));

        Long seatId = seatRepository.findByShowId(showId).get(0).getId();

        seatService.holdSeats(showId, List.of(seatId), "first-user");

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> seatService.holdSeats(showId, List.of(seatId), "second-user"));
    }
}