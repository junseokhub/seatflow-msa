package com.seatflow.show.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지수 백오프 + AWS equal jitter(delay/2 + rand(0, delay/2)) 계산을 검증한다.
 * jitter가 있어 결과값이 매번 달라지므로, 정확한 값이 아니라 "허용 범위 안에
 * 있는가"로 검증한다.
 */
class OutboxBackoffTest {

    @Nested
    @DisplayName("isExceeded()")
    class IsExceeded {

        @Test
        @DisplayName("MAX_RETRY 미만이면 false")
        void returnsFalseBelowMaxRetry() {
            assertThat(OutboxBackoff.isExceeded(OutboxBackoff.MAX_RETRY - 1)).isFalse();
        }

        @Test
        @DisplayName("정확히 MAX_RETRY에 도달하면 true (경계값)")
        void returnsTrueAtExactlyMaxRetry() {
            assertThat(OutboxBackoff.isExceeded(OutboxBackoff.MAX_RETRY)).isTrue();
        }

        @Test
        @DisplayName("MAX_RETRY를 초과해도 true")
        void returnsTrueAboveMaxRetry() {
            assertThat(OutboxBackoff.isExceeded(OutboxBackoff.MAX_RETRY + 5)).isTrue();
        }

        @Test
        @DisplayName("0회면 false")
        void returnsFalseAtZero() {
            assertThat(OutboxBackoff.isExceeded(0)).isFalse();
        }
    }

    @Nested
    @DisplayName("nextRetryAt()")
    class NextRetryAt {

        @RepeatedTest(20)
        @DisplayName("retryCount=1이면 base(1초) 근방의 jitter 범위 안에서 결과가 나온다")
        void firstRetryIsWithinJitterRangeOfBase() {
            LocalDateTime before = LocalDateTime.now();

            LocalDateTime result = OutboxBackoff.nextRetryAt(1);

            // exp = base * 2^(1-1) = base * 1 = 1000ms, jitter는 0~1000ms 사이 절반씩
            // 이론상 결과는 before + [0ms, 1000ms] 범위 안에 있어야 한다.
            assertThat(result).isAfterOrEqualTo(before);
            assertThat(result).isBeforeOrEqualTo(before.plus(Duration.ofSeconds(2)));   // 여유를 두고 확인
        }

        @RepeatedTest(20)
        @DisplayName("retryCount가 커져도 CAP(5분)을 절대 넘지 않는다")
        void neverExceedsCapEvenAtHighRetryCount() {
            LocalDateTime before = LocalDateTime.now();

            LocalDateTime result = OutboxBackoff.nextRetryAt(20);   // 지수적으로 매우 커질 시도 횟수

            assertThat(result).isBeforeOrEqualTo(before.plus(Duration.ofMinutes(6)));   // CAP(5분) + 여유
        }

        @Test
        @DisplayName("retryCount가 커질수록 대략적인 지연 시간도 늘어나는 경향을 보인다")
        void delayGenerallyIncreasesWithRetryCount() {
            LocalDateTime now = LocalDateTime.now();

            // 여러 번 측정해서 평균적인 경향을 본다(jitter 때문에 단일 비교는 불안정할 수 있음).
            long avgDelayAtRetry1 = averageDelayMillis(1, now);
            long avgDelayAtRetry5 = averageDelayMillis(5, now);

            assertThat(avgDelayAtRetry5).isGreaterThan(avgDelayAtRetry1);
        }

        private long averageDelayMillis(int retryCount, LocalDateTime baseline) {
            long total = 0;
            int samples = 30;
            for (int i = 0; i < samples; i++) {
                LocalDateTime result = OutboxBackoff.nextRetryAt(retryCount);
                total += Duration.between(baseline, result).toMillis();
            }
            return total / samples;
        }
    }
}