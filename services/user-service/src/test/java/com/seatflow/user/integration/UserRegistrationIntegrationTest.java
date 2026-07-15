package com.seatflow.user.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.user.domain.User;
import com.seatflow.user.repository.UserRepository;
import com.seatflow.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
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
 * UserService.createUser()의 noRollbackFor 멱등 전략이 진짜 동시 요청(at-least-once 재전달을 흉내낸 상황)에서도
 * 정확히 유저 1명만 만드는지 검증한다.
 * Mock으로는 "unique 제약 위반 시 조용히 무시한다"는 분기 로직만 확인할 수 있을 뿐,
 * 진짜 DB 레벨에서 여러 스레드가 동시에 같은 id로 insert를 시도했을 때 실제로 하나만 성공하고
 * 나머지가 정확히 그 예외 경로를 타는지는 진짜 DB가 있어야 증명된다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
class UserRegistrationIntegrationTest implements MysqlContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerDefaultProperties(registry);
        MysqlContainerSupport.registerMysqlProperties(registry);
    }

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("같은 user.registered 이벤트가 진짜 동시에 여러 번 도착해도(at-least-once 재현) 유저는 정확히 1명만 생성된다")
    void concurrentDuplicateRegistrationCreatesOnlyOneUser() throws InterruptedException {
        String userId = "concurrent-user-1";
        String email = "concurrent@example.com";
        int requestCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger noExceptionCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    userService.createUser(userId, email, "동시가입테스트");
                    noExceptionCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("Thread exception: " + e.getClass().getName() + " - " + e.getMessage());
                    if (e.getCause() != null) {
                        System.out.println("  Cause: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // noRollbackFor + 1062 무시 전략이므로, 중복 시도도 전부 "예외 없이" 끝나야 한다
        // (컨슈머 입장에서는 전부 ACK 가능한 정상 처리로 보인다).
        assertThat(noExceptionCount.get()).isEqualTo(requestCount);

        List<User> allUsers = userRepository.findAll();
        long matchingCount = allUsers.stream().filter(u -> u.getId().equals(userId)).count();
        assertThat(matchingCount).isEqualTo(1);   // 실제로 저장된 건 하나뿐
    }

    @Test
    @DisplayName("서로 다른 userId로 같은 email을 쓰는 요청이 동시에 몰려도 정확히 하나만 성공한다")
    void concurrentDifferentUserIdsSameEmailOnlyOneSucceeds() throws InterruptedException {
        String sharedEmail = "shared-email@example.com";
        int requestCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        for (int i = 0; i < requestCount; i++) {
            String userId = "user-with-shared-email-" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    userService.createUser(userId, sharedEmail, "이메일공유테스트");
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

        long matchingCount = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(sharedEmail))
                .count();
        assertThat(matchingCount).isEqualTo(1);   // email unique 제약으로 하나만 성공
    }
}