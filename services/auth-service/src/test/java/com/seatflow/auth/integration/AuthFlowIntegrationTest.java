package com.seatflow.auth.integration;

import com.seatflow.auth.exception.AuthErrorCode;
import com.seatflow.auth.repository.CredentialsRepository;
import com.seatflow.auth.service.AuthService;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 회원가입 -> 로그인 -> 토큰검증 -> 로그아웃 전체 흐름과, 계정 잠금의 동시성
 * 안전성을 진짜 MySQL + Redis 위에서 검증한다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private AuthService authService;
    @Autowired
    private CredentialsRepository credentialsRepository;

    @Test
    @DisplayName("회원가입 후 같은 이메일로 즉시 로그인할 수 있다")
    void canLoginImmediatelyAfterSignup() {
        authService.signup("flow-test1@example.com", "password123", "테스트유저");

        AuthService.TokenResult result = authService.login("flow-test1@example.com", "password123");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("발급받은 accessToken으로 validate가 성공한다")
    void issuedAccessTokenPassesValidation() {
        authService.signup("flow-test2@example.com", "password123", "테스트유저");
        AuthService.TokenResult loginResult = authService.login("flow-test2@example.com", "password123");

        AuthService.ValidateResult validateResult = authService.validate(loginResult.accessToken());

        assertThat(validateResult.email()).isEqualTo("flow-test2@example.com");
    }

    @Test
    @DisplayName("발급받은 refreshToken으로 재발급이 성공하고, 이전 refreshToken은 더 이상 못 쓴다")
    void refreshInvalidatesOldToken() throws InterruptedException {
        authService.signup("flow-test3@example.com", "password123", "테스트유저");
        AuthService.TokenResult firstLogin = authService.login("flow-test3@example.com", "password123");

        // JWT의 iat(issuedAt)은 초 단위라, login과 refresh가 같은 초 안에 실행되면
        // 발급되는 refreshToken 문자열이 우연히 완전히 똑같아질 수 있다.
        // "이전 토큰"과 "새 토큰"이 사실상 같은 값이라 무효화 검증 자체가 무의미해진다.
        // JwtProviderTest에서 겪었던 것과 같은 원인이라, 여기서도 최소 1초 이상
        // 간격을 둬서 서로 다른 토큰임을 확실히 한다.
        Thread.sleep(1100);

        AuthService.TokenResult refreshed = authService.refresh(firstLogin.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(firstLogin.refreshToken());   // 실제로 달라졌는지 먼저 확인

        // 이전 refreshToken은 Redis에 저장된 최신 값과 더 이상 일치하지 않으므로 거부돼야 한다.
        assertThatThrownBy(() -> authService.refresh(firstLogin.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthErrorCode.INVALID_TOKEN.getMessage());
    }

    @Test
    @DisplayName("로그아웃한 accessToken으로는 더 이상 validate가 통과하지 않는다")
    void loggedOutTokenFailsValidation() {
        authService.signup("flow-test4@example.com", "password123", "테스트유저");
        AuthService.TokenResult loginResult = authService.login("flow-test4@example.com", "password123");

        authService.logout(loginResult.accessToken(), loginResult.refreshToken());

        assertThatThrownBy(() -> authService.validate(loginResult.accessToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthErrorCode.INVALID_TOKEN.getMessage());
    }

    @Test
    @DisplayName("비밀번호를 5번 연속 틀리면 계정이 잠기고, 그 이후엔 올바른 비밀번호로도 로그인할 수 없다")
    void accountLocksAfterFiveFailuresEvenWithCorrectPasswordAfterward() {
        authService.signup("flow-test5@example.com", "correctPassword", "테스트유저");

        for (int i = 0; i < 5; i++) {
            try {
                authService.login("flow-test5@example.com", "wrongPassword");
            } catch (BusinessException ignored) {
            }
        }

        assertThatThrownBy(() -> authService.login("flow-test5@example.com", "correctPassword"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());
    }

    @Test
    @DisplayName("같은 이메일로 진짜 동시에 회원가입을 시도해도 정확히 한 명만 성공한다")
    void concurrentSignupsWithSameEmailOnlyOneSucceeds() throws InterruptedException {
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
                    authService.signup("concurrent-signup@example.com", "password123", "동시가입테스트");
                    successCount.incrementAndGet();
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

        assertThat(successCount.get()).isEqualTo(1);

        long savedCount = credentialsRepository.findAll().stream()
                .filter(c -> c.getEmail().equals("concurrent-signup@example.com"))
                .count();
        assertThat(savedCount).isEqualTo(1);
    }
}