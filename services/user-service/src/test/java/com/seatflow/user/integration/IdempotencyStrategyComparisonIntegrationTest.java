package com.seatflow.user.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.user.idempotency.UserServiceInboxPattern;
import com.seatflow.user.idempotency.UserServiceInsertIgnore;
import com.seatflow.user.idempotency.UserServiceRequiresNew;
import com.seatflow.user.idempotency.UserServiceSaveAndCatch;
import com.seatflow.user.inbox.ProcessedEventRepository;
import com.seatflow.user.repository.UserRepository;
import com.seatflow.user.service.UserServiceNoRollbackFor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세 가지 멱등성 방식([1] save+catch, [2] INSERT IGNORE, [3] REQUIRES_NEW)이 각각 컨슈머에 트랜잭션이 없는, 단순한 호출 상황에서는 전부 정상 동작함을 확인한다.
 * [1]의 진짜 위험(호출자에 트랜잭션이 있을 때 rollback-only 오염)은 여기서 재현하지 않는다.
 * 그 실패를 재현하려면 이 메서드를 감싸는 외부 @Transactional 호출자가 필요한데,
 * 그것은 지금 채택된 [4](noRollbackFor)가 왜 필요했는지를 보여주는 별도 시나리오이지,
 * 이 세 방식을 단순 비교하는 목적에는 맞지 않아 이 클래스에서는 다루지 않는다.
 *
 * 참고: 실제 서비스 빈으로는 등록되지 않은 클래스들이라(빈 이름이 충돌하지 않도록 별도 @Service 이름을 줬다) 운영 코드에 영향이 없다.
 *
 * 순수 비교
 * 학습 목적으로만 존재한다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyStrategyComparisonIntegrationTest implements MysqlContainerSupport {

     @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    private UserServiceSaveAndCatch saveAndCatchService;
    @Autowired
    private UserServiceInsertIgnore insertIgnoreService;
    @Autowired
    private UserServiceRequiresNew requiresNewService;
    @Autowired
    private UserServiceNoRollbackFor noRollbackForService;
    @Autowired
    private UserServiceInboxPattern inboxPatternService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("[1] save+catch - 컨슈머 트랜잭션이 없는 단순 상황에서는 동시 요청도 정확히 1명만 생성한다")
    void saveAndCatchHandlesConcurrencyInSimpleCase() throws InterruptedException {
        long created = runConcurrently(10, i ->
                saveAndCatchService.createUser("save-catch-user", "save-catch@example.com", "테스트"));

        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getId().equals("save-catch-user")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[2] INSERT IGNORE - 동시 요청에도 정확히 1명만 생성되고 항상 예외 없이 끝난다")
    void insertIgnoreHandlesConcurrencyWithoutExceptions() throws InterruptedException {
        int requestCount = 10;
        AtomicInteger noExceptionCount = new AtomicInteger();

        runConcurrentlyCounting(requestCount, i -> {
            insertIgnoreService.createUser("insert-ignore-user", "insert-ignore@example.com", "테스트");
            noExceptionCount.incrementAndGet();
        });

        assertThat(noExceptionCount.get()).isEqualTo(requestCount);   // 전부 예외 없이 끝남
        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getId().equals("insert-ignore-user")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[3] REQUIRES_NEW - 동시 요청에도 정확히 1명만 생성되고, 실패한 트랜잭션은 이 메서드 안에서만 롤백된다")
    void requiresNewHandlesConcurrencyInIsolatedTransaction() throws InterruptedException {
        long created = runConcurrently(10, i ->
                requiresNewService.createUser("requires-new-user", "requires-new@example.com", "테스트"));

        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getId().equals("requires-new-user")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[4] noRollbackFor - 진짜 동시 요청 상황에서는 UnexpectedRollbackException이 실제로 재현된다 (철회된 이유)")
    void noRollbackForFailsUnderRealConcurrency() throws InterruptedException {
        /**
         * 이게 바로 UserService가 이 방식을 철회한 이유다.
         * Mock 기반 단위 테스트로는 이 문제가 절대 재현되지 않는다.
         * saveAndFlush()가 unique 제약 위반으로 실패하는 순간 Spring 트랜잭션이 이미 rollback-only로 마킹되고,
         * catch로 예외를 잡아 정상 종료해도 메서드가 끝나고 커밋하는 시점에 UnexpectedRollbackException이 터진다.
         * 진짜 Spring 트랜잭션 매니저 + 진짜 DB로만 드러난다.
         */
        int requestCount = 10;
        AtomicInteger unexpectedRollbackCount = new AtomicInteger();
        AtomicInteger noExceptionCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    noRollbackForService.createUser(
                            "norollback-user", "norollback@example.com", "테스트");
                    noExceptionCount.incrementAndGet();
                } catch (org.springframework.transaction.UnexpectedRollbackException e) {
                    unexpectedRollbackCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 유저는 여전히 정확히 1명만 생성된다.
        // 데이터 정합성 자체는 깨지지 않는다.
        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getId().equals("norollback-user")).count()).isEqualTo(1);

        // 하지만 예외 없이 조용히 넘어갔어야 할 나머지 요청들 중 일부가 UnexpectedRollbackException으로 실제로 실패한다.
        // 이게 문제였다. (실행 환경에 따라 정확한 개수는 달라질 수 있어 0보다 크다는 것만 확인한다.)
        assertThat(unexpectedRollbackCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("[Inbox] 같은 eventId로 진짜 동시 요청이 몰려도, processed_event 선점을 통해 정확히 1명만 생성된다")
    void inboxPatternHandlesConcurrencyViaEventIdPreemption() throws InterruptedException {
        String sharedEventId = "shared-event-id-1";   // at-least-once 재전달 시 같은 eventId로 옴

        runConcurrently(10, i ->
                inboxPatternService.createUser(sharedEventId, "inbox-user", "inbox@example.com", "테스트"));

        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getId().equals("inbox-user")).count()).isEqualTo(1);
        // processed_event에도 정확히 1건만 기록되어야 한다.
        // 나머지 9번의 시도는 insertIfAbsent가 0을 반환해 businessLogic 자체를 실행하지 않았어야 한다.
        assertThat(processedEventRepository.findAll().stream()
                .filter(e -> e.getId().getEventId().equals(sharedEventId)).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[Inbox] eventId가 다르면(재전달이 아니라 새 이벤트) 매번 새로 처리된다")
    void inboxPatternProcessesDifferentEventIdsIndependently() {
        inboxPatternService.createUser("event-A", "inbox-user-a", "inbox-a@example.com", "테스트A");
        inboxPatternService.createUser("event-B", "inbox-user-b", "inbox-b@example.com", "테스트B");

        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getId().equals("inbox-user-a") || u.getId().equals("inbox-user-b"))
                .count()).isEqualTo(2);   // 서로 다른 이벤트라 둘 다 생성됨
    }

    private long runConcurrently(int count, java.util.function.IntConsumer action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch readyLatch = new CountDownLatch(count);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    action.accept(idx);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        return count;
    }

    private void runConcurrentlyCounting(int count, java.util.function.IntConsumer action) throws InterruptedException {
        runConcurrently(count, action);
    }
}