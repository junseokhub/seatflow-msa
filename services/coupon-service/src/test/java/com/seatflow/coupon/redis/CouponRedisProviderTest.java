package com.seatflow.coupon.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Lua 스크립트 자체의 원자성/정확성은 Mock으로 검증할 수 없다(진짜 Redis가
 * 필요 — 통합 테스트 영역). 여기서는 CouponRedisProvider가 RedisTemplate을
 * "올바른 키, 올바른 인자로" 호출하는지만 확인한다 — 예를 들어 캠페인ID와
 * 사용자ID로 키를 조합하는 로직에 오타나 실수가 없는지.
 */
@ExtendWith(MockitoExtension.class)
class CouponRedisProviderTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private CouponRedisProvider couponRedisProvider;

    @BeforeEach
    void setUp() {
        couponRedisProvider = new CouponRedisProvider(redisTemplate);
    }

    @Test
    @DisplayName("issue()는 remaining/issued 키를 올바르게 조합해 execute를 호출한다")
    void issueCallsExecuteWithCorrectKeys() {
        given(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("coupon:campaign:1:remaining", "coupon:campaign:1:issued:user1")),
                any()))
                .willReturn(1L);

        long result = couponRedisProvider.issue(1L, "user1");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("execute 결과가 null이면 0(재고소진 취급)을 반환한다")
    void issueReturnsZeroWhenExecuteReturnsNull() {
        given(redisTemplate.execute(any(DefaultRedisScript.class), any(), any()))
                .willReturn(null);

        long result = couponRedisProvider.issue(1L, "user1");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("confirmIssued()는 issued 키 하나로만 execute를 호출한다")
    void confirmIssuedCallsExecuteWithIssuedKeyOnly() {
        given(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("coupon:campaign:1:issued:user1"))))
                .willReturn(1L);

        couponRedisProvider.confirmIssued(1L, "user1");

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("coupon:campaign:1:issued:user1")));
    }

    @Test
    @DisplayName("initializeStock()은 remaining 키에 총 수량을 문자열로 저장한다")
    void initializeStockSetsRemainingKey() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        couponRedisProvider.initializeStock(1L, 100);

        verify(valueOperations).set("coupon:campaign:1:remaining", "100");
    }

    @Test
    @DisplayName("restoreStock()은 remaining을 1 증가시키고 issued 키를 삭제한다")
    void restoreStockIncrementsRemainingAndDeletesIssuedKey() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        couponRedisProvider.restoreStock(1L, "user1");

        verify(valueOperations).increment("coupon:campaign:1:remaining");
        verify(redisTemplate).delete("coupon:campaign:1:issued:user1");
    }

    @Test
    @DisplayName("getRemaining()은 remaining 키 값을 Long으로 파싱해 반환한다")
    void getRemainingParsesValueAsLong() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("coupon:campaign:1:remaining")).willReturn("42");

        Long result = couponRedisProvider.getRemaining(1L);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("getRemaining()은 키가 없으면 null을 반환한다")
    void getRemainingReturnsNullWhenKeyMissing() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("coupon:campaign:1:remaining")).willReturn(null);

        Long result = couponRedisProvider.getRemaining(1L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("restoreLeakedStock()은 remaining을 지정한 양만큼 증가시킨다")
    void restoreLeakedStockIncrementsByGivenAmount() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        couponRedisProvider.restoreLeakedStock(1L, 3L);

        verify(valueOperations).increment("coupon:campaign:1:remaining", 3L);
    }
}