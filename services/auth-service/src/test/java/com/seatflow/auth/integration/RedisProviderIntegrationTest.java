package com.seatflow.auth.integration;

import com.seatflow.auth.redis.RedisProvider;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedisProviderIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerDefaultProperties(registry);
        MysqlContainerSupport.registerMysqlProperties(registry);
    }

    @Autowired
    private RedisProvider redisProvider;

    @Test
    @DisplayName("refreshToken을 저장하고 조회하면 정확히 같은 값이 나온다")
    void savesAndRetrievesRefreshToken() {
        redisProvider.saveRefreshToken("user-1", "refresh-token-value", 60000);

        String result = redisProvider.getRefreshToken("user-1");

        assertThat(result).isEqualTo("refresh-token-value");
    }

    @Test
    @DisplayName("deleteRefreshToken() 이후 조회하면 null이 나온다")
    void deletedRefreshTokenReturnsNull() {
        redisProvider.saveRefreshToken("user-2", "token", 60000);

        redisProvider.deleteRefreshToken("user-2");

        assertThat(redisProvider.getRefreshToken("user-2")).isNull();
    }

    @Test
    @DisplayName("블랙리스트에 등록한 토큰은 isBlacklisted()가 true를 반환한다")
    void blacklistedTokenIsDetected() {
        redisProvider.addBlacklist("access-token-1", 60000);

        assertThat(redisProvider.isBlacklisted("access-token-1")).isTrue();
    }

    @Test
    @DisplayName("블랙리스트에 없는 토큰은 isBlacklisted()가 false를 반환한다")
    void nonBlacklistedTokenIsNotDetected() {
        assertThat(redisProvider.isBlacklisted("never-blacklisted-token")).isFalse();
    }

    @Test
    @DisplayName("로그인 실패 카운트는 호출할 때마다 1씩 증가한다")
    void incrementsLoginFailCountEachCall() {
        long first = redisProvider.incrementLoginFail("fail-test@example.com");
        long second = redisProvider.incrementLoginFail("fail-test@example.com");
        long third = redisProvider.incrementLoginFail("fail-test@example.com");

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(third).isEqualTo(3L);
    }

    @Test
    @DisplayName("resetLoginFail() 이후에는 계정 잠금 상태가 아니다")
    void resetLoginFailUnlocksAccount() {
        String email = "reset-test@example.com";
        for (int i = 0; i < 5; i++) {
            redisProvider.incrementLoginFail(email);
        }
        assertThat(redisProvider.isAccountLocked(email)).isTrue();   // 5회 도달, 잠김

        redisProvider.resetLoginFail(email);

        assertThat(redisProvider.isAccountLocked(email)).isFalse();
    }

    @Test
    @DisplayName("로그인 실패가 5회 미만이면 계정이 잠기지 않는다")
    void accountNotLockedBeforeFiveFailures() {
        String email = "under-limit@example.com";
        for (int i = 0; i < 4; i++) {
            redisProvider.incrementLoginFail(email);
        }

        assertThat(redisProvider.isAccountLocked(email)).isFalse();
    }

    @Test
    @DisplayName("로그인 실패가 정확히 5회에 도달하면 계정이 잠긴다")
    void accountLockedAtExactlyFiveFailures() {
        String email = "exact-limit@example.com";
        for (int i = 0; i < 5; i++) {
            redisProvider.incrementLoginFail(email);
        }

        assertThat(redisProvider.isAccountLocked(email)).isTrue();
    }
}