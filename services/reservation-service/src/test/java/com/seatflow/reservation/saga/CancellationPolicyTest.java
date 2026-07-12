package com.seatflow.reservation.saga;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 취소 수수료 정책의 날짜 경계값을 정확히 검증한다. 정책 자체가 "N일 이상이면
 * X%"라는 경계 조건 나열이라, 정확히 그 경계(9일째와 10일째, 6일째와 7일째 등)를
 * 하나씩 짚어야 실수를 잡을 수 있다 — 코드를 눈으로만 읽어서는 "9.99일"처럼
 * 애매한 경우의 반올림/버림 방향을 확신하기 어렵다.
 */
class CancellationPolicyTest {

    private static final LocalDateTime SHOW_DATE = LocalDateTime.of(2026, 12, 25, 19, 0);

    @Nested
    @DisplayName("isCancellable()")
    class IsCancellable {

        @Test
        @DisplayName("공연 1일 전이면 취소 가능하다")
        void oneDayBeforeIsCancellable() {
            LocalDateTime now = SHOW_DATE.minusDays(1);

            assertThat(CancellationPolicy.isCancellable(SHOW_DATE, now)).isTrue();
        }

        @Test
        @DisplayName("공연 당일이면 취소 불가능하다")
        void sameDayIsNotCancellable() {
            LocalDateTime now = SHOW_DATE.minusHours(2);   // 공연 몇 시간 전, 같은 날

            assertThat(CancellationPolicy.isCancellable(SHOW_DATE, now)).isFalse();
        }

        @Test
        @DisplayName("공연이 이미 지났으면 취소 불가능하다")
        void afterShowIsNotCancellable() {
            LocalDateTime now = SHOW_DATE.plusHours(1);

            assertThat(CancellationPolicy.isCancellable(SHOW_DATE, now)).isFalse();
        }
    }

    @Nested
    @DisplayName("calculateRefund() 경계값")
    class CalculateRefundBoundaries {

        @Test
        @DisplayName("정확히 10일 전이면 전액 환불(수수료 0%)")
        void exactlyTenDaysGetsFullRefund() {
            LocalDateTime now = SHOW_DATE.minusDays(10);

            BigDecimal refund = CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now);

            assertThat(refund).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("9일 전이면 10% 수수료(90% 환불) — 10일 경계 바로 아래")
        void nineDaysGets90PercentRefund() {
            LocalDateTime now = SHOW_DATE.minusDays(9);

            BigDecimal refund = CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now);

            assertThat(refund).isEqualByComparingTo("9000.00");
        }

        @Test
        @DisplayName("정확히 7일 전이면 10% 수수료(90% 환불)")
        void exactlySevenDaysGets90PercentRefund() {
            LocalDateTime now = SHOW_DATE.minusDays(7);

            BigDecimal refund = CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now);

            assertThat(refund).isEqualByComparingTo("9000.00");
        }

        @Test
        @DisplayName("6일 전이면 20% 수수료(80% 환불) — 7일 경계 바로 아래")
        void sixDaysGets80PercentRefund() {
            LocalDateTime now = SHOW_DATE.minusDays(6);

            BigDecimal refund = CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now);

            assertThat(refund).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("정확히 4일 전이면 20% 수수료(80% 환불)")
        void exactlyFourDaysGets80PercentRefund() {
            LocalDateTime now = SHOW_DATE.minusDays(4);

            BigDecimal refund = CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now);

            assertThat(refund).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("3일 전이면 30% 수수료(70% 환불) — 4일 경계 바로 아래")
        void threeDaysGets70PercentRefund() {
            LocalDateTime now = SHOW_DATE.minusDays(3);

            BigDecimal refund = CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now);

            assertThat(refund).isEqualByComparingTo("7000.00");
        }

        @Test
        @DisplayName("정확히 1일 전이면 30% 수수료(70% 환불) — 취소 가능한 마지노선")
        void exactlyOneDayGets70PercentRefund() {
            LocalDateTime now = SHOW_DATE.minusDays(1);

            BigDecimal refund = CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now);

            assertThat(refund).isEqualByComparingTo("7000.00");
        }

        @Test
        @DisplayName("취소 가능 기간(1일)이 지난 시점에 계산을 시도하면 예외를 던진다")
        void throwsWhenPastCancellableWindow() {
            LocalDateTime now = SHOW_DATE.minusHours(2);   // 0일 남음

            assertThatThrownBy(() -> CancellationPolicy.calculateRefund(
                    BigDecimal.valueOf(10000), SHOW_DATE, now))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
    
    @ParameterizedTest(name = "{0}일 남았을 때 환불 비율은 {1}")
    @CsvSource({
            "15, 1.00",
            "10, 1.00",
            "9, 0.90",
            "7, 0.90",
            "6, 0.80",
            "4, 0.80",
            "3, 0.70",
            "1, 0.70"
    })
    @DisplayName("전체 구간을 한 번에 파라미터화해서 확인")
    void refundRateAcrossAllBoundaries(long daysBefore, String expectedRate) {
        LocalDateTime now = SHOW_DATE.minusDays(daysBefore);
        BigDecimal expected = BigDecimal.valueOf(10000)
                .multiply(new BigDecimal(expectedRate))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal refund = CancellationPolicy.calculateRefund(
                BigDecimal.valueOf(10000), SHOW_DATE, now);

        assertThat(refund).isEqualByComparingTo(expected);
    }
}