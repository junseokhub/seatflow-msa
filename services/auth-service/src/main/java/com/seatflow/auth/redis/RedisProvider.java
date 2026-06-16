package com.seatflow.auth.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisProvider {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final int MAX_LOGIN_FAIL = 5;

    // refresh token 저장
    public void saveRefreshToken(String userId, String token, long expiration) {
        redisTemplate.opsForValue()
                .set(REFRESH_PREFIX + userId, token, expiration, TimeUnit.MILLISECONDS);
    }

    // refresh token 조회
    public String getRefreshToken(String userId) {
        return redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
    }

    // refresh token 삭제
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }

    // access token blacklist 등록
    public void addBlacklist(String token, long remaining) {
        redisTemplate.opsForValue()
                .set(BLACKLIST_PREFIX + token, "1", remaining, TimeUnit.MILLISECONDS);
    }

    // blacklist 확인
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    // 로그인 실패 횟수 증가
    public long incrementLoginFail(String email) {
        String key = LOGIN_FAIL_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 15, TimeUnit.MINUTES);
        }
        return count == null ? 0 : count;
    }
    // 로그인 실패 횟수 초기화
    public void resetLoginFail(String email) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + email);
    }

    // 계정 잠금 확인
    public boolean isAccountLocked(String email) {
        String count = redisTemplate.opsForValue().get(LOGIN_FAIL_PREFIX + email);
        return count != null && Integer.parseInt(count) >= MAX_LOGIN_FAIL;
    }
}