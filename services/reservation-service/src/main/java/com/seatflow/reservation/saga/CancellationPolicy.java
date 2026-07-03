package com.seatflow.reservation.saga;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 취소 수수료 정책. 공연일(showDate)까지 남은 기간에 따라 환불 금액을 계산한다.
 * 취소 요청 시점 기준으로 공연이 얼마나 남았는지를 보고 수수료를 뗀다.
 *
 * 단발성 공연(1회)을 가정한다. 회차별 공연은 회차 일시 기준으로 계산하도록 향후 확장한다.
 *
 * 정책(일반적인 티켓팅 기준):
 *   관람 10일 전까지  → 전액 환불 (수수료 0%)
 *   관람 7~9일 전     → 10% 수수료
 *   관람 4~6일 전     → 20% 수수료
 *   관람 1~3일 전     → 30% 수수료
 *   관람 당일 이후    → 취소 불가
 */
public final class CancellationPolicy {

    private CancellationPolicy() {}

    /**
     * 취소 가능 여부. 공연 당일(0일) 이후는 취소할 수 없다.
     */
    public static boolean isCancellable(LocalDateTime showDate, LocalDateTime now) {
        return daysUntilShow(showDate, now) >= 1;
    }

    /**
     * 환불 금액을 계산한다. 남은 일수에 따라 수수료를 뗀 금액을 반환한다.
     * 취소 불가 시점이면 예외를 던진다(호출 전에 isCancellable로 확인하는 것을 전제).
     */
    public static BigDecimal calculateRefund(BigDecimal amount, LocalDateTime showDate, LocalDateTime now) {
        long days = daysUntilShow(showDate, now);

        BigDecimal rate;   // 환불 비율
        if (days >= 10) {
            rate = new BigDecimal("1.00");   // 전액
        } else if (days >= 7) {
            rate = new BigDecimal("0.90");   // 10% 수수료
        } else if (days >= 4) {
            rate = new BigDecimal("0.80");   // 20% 수수료
        } else if (days >= 1) {
            rate = new BigDecimal("0.70");   // 30% 수수료
        } else {
            throw new IllegalStateException("취소 가능 기간이 지났다: daysUntilShow=" + days);
        }

        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 공연일까지 남은 일수. 날짜 경계가 아니라 24시간 단위로 센다.
     * (예: 지금이 8/30 20:00이고 공연이 9/1 19:00이면 약 1.96일 → 1일)
     */
    private static long daysUntilShow(LocalDateTime showDate, LocalDateTime now) {
        return Duration.between(now, showDate).toDays();
    }
}