package com.seatflow.seat.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SeatRedisProvider {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String SEAT_HOLD_PREFIX = "seat:hold:";
    private static final long HOLD_TTL_MINUTES = 5;

    // GET -> 소유자 비교 -> 일치하면 DEL, 전부 한 번에 (원자적)
    private static final RedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>("""
        local holder = redis.call('GET', KEYS[1])
        if holder == false then
            return -1
        elseif holder == ARGV[1] then
            redis.call('DEL', KEYS[1])
            return 1
        else
            return 0
        end
        """, Long.class);

    public long releaseIfOwner(String showId, Long seatId, String userId) {
        String key = SEAT_HOLD_PREFIX + showId + ":" + seatId;
        Long result = redisTemplate.execute(RELEASE_IF_OWNER, List.of(key), userId);
        return result == null ? -1 : result;
    }

    // 좌석 임시 점유 (SETNX - 원자적 연산) -> 명령 하나 단위
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