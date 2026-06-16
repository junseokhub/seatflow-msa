package com.seatflow.seat.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SeatRedisProvider {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String SEAT_HOLD_PREFIX = "seat:hold:";
    private static final long HOLD_TTL_MINUTES = 5;

    // 좌석 임시 점유 (SETNX - 원자적 연산)
    public boolean hold(String showId, Long seatId, String userId) {
        String key = SEAT_HOLD_PREFIX + showId + ":" + seatId;
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, HOLD_TTL_MINUTES, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(result);
    }

    // 좌석 점유자 조회
    public String getHolder(String showId, Long seatId) {
        String key = SEAT_HOLD_PREFIX + showId + ":" + seatId;
        return redisTemplate.opsForValue().get(key);
    }

    // 좌석 점유 해제
    public void release(String showId, Long seatId) {
        String key = SEAT_HOLD_PREFIX + showId + ":" + seatId;
        redisTemplate.delete(key);
    }

    // 점유 여부 확인
    public boolean isHeld(String showId, Long seatId) {
        String key = SEAT_HOLD_PREFIX + showId + ":" + seatId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}