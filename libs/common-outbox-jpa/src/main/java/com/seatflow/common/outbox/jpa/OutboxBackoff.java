package com.seatflow.common.outbox.jpa;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 재시도 정책(지수 백오프 + equal jitter)과 한계 상수의 단일 출처. 순수 계산이라 DB 의존이 없다.
 */
public final class OutboxBackoff {

    private OutboxBackoff() {}

    public static final int MAX_RETRY = 10;                          // 캡 백오프 기준 ≈ 13분 버팀
    private static final Duration BASE = Duration.ofSeconds(1);
    private static final Duration CAP = Duration.ofMinutes(5);

    public static boolean isExceeded(int retryCount) {
        return retryCount >= MAX_RETRY;
    }

    /** AWS equal jitter: delay/2 + rand(0, delay/2) */
    public static LocalDateTime nextRetryAt(int retryCount) {
        long expMs = (long) (BASE.toMillis() * Math.pow(2, retryCount - 1));
        long delayMs = Math.min(expMs, CAP.toMillis());
        long half = delayMs / 2;
        long jittered = half + ThreadLocalRandom.current().nextLong(half + 1);
        return LocalDateTime.now().plus(Duration.ofMillis(jittered));
    }
}