package com.seatflow.coupon.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 쿠폰 발급의 1차 판단(재고 차감, 1인 1매)을 Redis에서 원자적으로 처리한다.
 *
 * Redis 성공과 MySQL 저장 사이에는 시간차가 있다(같은 트랜잭션으로 묶을 수 없다 서로 다른 저장소이므로).
 * 이 사이에 서버가 죽거나 DB 커넥션이 끊기는 등 예측 못한 장애가 나면, Redis는 "발급 완료"로 마킹됐는데 MySQL엔 아무 기록도 없는 상태가 영구히 남을 수 있다.
 * 그 사용자는 실제 쿠폰 없이 "이미 발급받음"으로 계속 거부당한다.
 *
 * 이걸 막기 위해 발급 마킹(issued 키)에 짧은 TTL을 건다. MySQL 저장까지 성공해야 confirmIssued()가 이 TTL을 없애 "영구 확정"으로 바꾼다.
 * 그 전에 뭐가 실패하든 (예외를 못 잡는 경우까지 포함해서) TTL이 지나면 마킹이 저절로 사라져 재시도가 가능해진다.
 *  별도 배치나 정합성 점검 없이 Redis 자체 기능만으로 복구된다.
 *
 * 재고(remaining) 카운터는 TTL을 걸지 않는다.
 * 재고 자체는 마킹 실패 여부와 무관하게 "일단 차감된 채로" 두고,
 * confirmIssued 실패 시(TTL 만료) 그 재고를 되돌리는 것도 명시적으로 처리한다(아래 releaseExpiredHold 참고)
 * 다만 이건 TTL 만료를 감지하는 별도 수단이 필요해 지금 범위에서는 "임시 마킹 동안은 재고가 묶여있다가, confirm 성공 시에만 실제로 확정 소비된다"는 정도로 단순화한다).
 */
@Component
@RequiredArgsConstructor
public class CouponRedisProvider {

    private static final long PENDING_TTL_SECONDS = 30L;   // MySQL 저장에 이 시간 안에 성공해야 함

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> issueCouponScript = loadIssueScript();
    private final DefaultRedisScript<Long> confirmCouponScript = loadConfirmScript();

    /**
     * "재고 확인 + 차감 + 임시 발급 마킹(TTL)"을 하나의 원자적 연산으로 묶는다.
     * KEYS[1] = coupon:campaign:{campaignId}:remaining
     * KEYS[2] = coupon:campaign:{campaignId}:issued:{userId}
     * ARGV[1] = TTL(초)
     * 반환값: 1 = 임시 발급 성공(TTL 걸림), 0 = 재고 소진, -1 = 이미 발급됨/발급 시도 중
     */
    private static DefaultRedisScript<Long> loadIssueScript() {
        String script = """
                if redis.call("EXISTS", KEYS[2]) == 1 then
                    return -1
                end

                local remaining = tonumber(redis.call("GET", KEYS[1]))
                if remaining == nil or remaining <= 0 then
                    return 0
                end

                redis.call("DECR", KEYS[1])
                redis.call("SET", KEYS[2], "PENDING", "EX", ARGV[1])

                return 1
                """;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    /**
     * MySQL 저장 성공 후 호출. 임시 마킹(PENDING, TTL 있음)을 영구 확정(CONFIRMED,
     * TTL 없음)으로 바꾼다. 이 호출 자체가 안 오면(MySQL 저장 실패, 서버 다운 등)
     * TTL이 그대로 흘러가 자동으로 마킹이 사라진다 — confirmIssued를 호출하지
     * "않는 것"이 곧 복구 트리거다.
     * KEYS[1] = coupon:campaign:{campaignId}:issued:{userId}
     * 반환값: 1 = 확정 성공, 0 = 이미 만료됐거나 없음(비정상 상황, 로그로 남길 것)
     */
    private static DefaultRedisScript<Long> loadConfirmScript() {
        String script = """
                if redis.call("EXISTS", KEYS[1]) == 0 then
                    return 0
                end

                redis.call("PERSIST", KEYS[1])
                redis.call("SET", KEYS[1], "CONFIRMED")

                return 1
                """;
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    /**
     * @return 1 = 임시 발급 성공(뒤이어 반드시 confirmIssued 또는 restoreStock 호출 필요),
     *         0 = 재고 소진, -1 = 이미 발급됨(또는 발급 시도 중)
     */
    public long issue(Long campaignId, String userId) {
        Long result = redisTemplate.execute(
                issueCouponScript,
                List.of(remainingKey(campaignId), issuedKey(campaignId, userId)),
                String.valueOf(PENDING_TTL_SECONDS));
        return result == null ? 0L : result;
    }

    /** MySQL 저장 성공 후 반드시 호출 — 임시 마킹을 영구로 확정한다. */
    public void confirmIssued(Long campaignId, String userId) {
        Long result = redisTemplate.execute(
                confirmCouponScript,
                List.of(issuedKey(campaignId, userId)));
        if (result == null || result == 0L) {
            // TTL이 이미 지나 마킹이 사라진 뒤 confirm이 뒤늦게 온 비정상 케이스.
            // MySQL엔 저장됐는데 Redis 마킹이 없어진 상태라, 이 사용자가 재시도하면
            // Redis는 "발급 가능"으로 보고 다시 발급을 내줄 수 있다 — 이 경우 MySQL의
            // unique 제약(campaignId, userId)이 최후 방어선이 되어 중복 저장을 막는다.
        }
    }

    /** 캠페인 생성 시 Redis에 재고를 초기화한다. */
    public void initializeStock(Long campaignId, int totalQuantity) {
        redisTemplate.opsForValue().set(remainingKey(campaignId), String.valueOf(totalQuantity));
    }

    /**
     * MySQL 저장이 "예외로 잡아낼 수 있는" 방식으로 실패했을 때 명시적으로 호출해
     * 재고와 마킹을 즉시 되돌린다. 예외 없이 실패하는 경우(서버 다운 등)는 이
     * 메서드가 호출될 기회조차 없으므로, 그 경우는 issue()가 건 TTL이 대신 처리한다.
     */
    public void restoreStock(Long campaignId, String userId) {
        redisTemplate.opsForValue().increment(remainingKey(campaignId));
        redisTemplate.delete(issuedKey(campaignId, userId));
    }

    private String remainingKey(Long campaignId) {
        return "coupon:campaign:" + campaignId + ":remaining";
    }

    private String issuedKey(Long campaignId, String userId) {
        return "coupon:campaign:" + campaignId + ":issued:" + userId;
    }
    ///////////////////////////

    /** 정합성 점검(CouponStockReconciliationScheduler)이 현재 remaining 값을 읽는 데 쓴다. */
    public Long getRemaining(Long campaignId) {
        String value = redisTemplate.opsForValue().get(remainingKey(campaignId));
        return value == null ? null : Long.parseLong(value);
    }

    /**
     * 정합성 점검이 누수를 발견했을 때, 계산된 보정값만큼 재고를 되돌린다.
     * amount는 항상 양수(되돌릴 재고량)로 전달한다.
     */
    public void restoreLeakedStock(Long campaignId, long amount) {
        redisTemplate.opsForValue().increment(remainingKey(campaignId), amount);
    }
}