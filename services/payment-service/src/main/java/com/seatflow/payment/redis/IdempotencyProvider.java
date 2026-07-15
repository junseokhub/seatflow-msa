package com.seatflow.payment.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 결제 요청의 인프라 레벨 멱등성(1층). 같은 Idempotency-Key를 가진 요청이
 * 광클·네트워크 재시도로 여러 번 도착해도 한 번만 처리되게 한다.
 *
 * Redis에 키를 SET NX로 선점하며, 확인과 선점을 Lua로 원자 실행해 check-then-act 틈을 없앤다.
 *   - 처음    : 키 없음 ->PROCESSING 기록 ->1 반환(진행 허용)
 *   - 처리 중 : 이미 PROCESSING ->0 반환(동시/재요청 거절)
 *   - 완료    : DONE 기록되어 있으면 -1 반환(이미 끝난 요청)
 * 비즈니스 레벨 중복(이미 성공한 예매에 다른 키로 결제)은 DB 제약(2층)이 최종 차단한다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyProvider {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "payment:idem:";
    private static final long TTL_SECONDS = 600;   // 10분: 재시도 창. 결제 처리 시간보다 충분히 길게.

    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_DONE = "DONE";

    // 키가 없으면 PROCESSING 기록 후 1, PROCESSING이면 0, DONE이면 -1
    private static final RedisScript<Long> TRY_ACQUIRE = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if v == false then
                redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
                return 1
            elseif v == ARGV[3] then
                return -1
            else
                return 0
            end
            """, Long.class);

    /**
     * 키 선점 시도.
     * @return 1=처음(진행), 0=처리 중(거절), -1=이미 완료(거절)
     */
    public long tryAcquire(String idempotencyKey) {
        Long result = redisTemplate.execute(
                TRY_ACQUIRE,
                List.of(key(idempotencyKey)),
                STATE_PROCESSING, String.valueOf(TTL_SECONDS), STATE_DONE);
        return result == null ? 0 : result;
    }

    /** 처리 완료로 표시(같은 키 재요청이 -1로 거절되게). TTL은 유지해 일정 기간 후 자동 정리. */
    public void markDone(String idempotencyKey) {
        redisTemplate.opsForValue().set(key(idempotencyKey), STATE_DONE, TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);
    }

    /** 처리 실패 시 키를 제거해 동일 키로 재시도할 수 있게 한다. */
    public void release(String idempotencyKey) {
        redisTemplate.delete(key(idempotencyKey));
    }

    private String key(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }
}