package com.seatflow.coupon.integration;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.coupon.redis.CouponRedisProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponRedisProvider의 개별 메서드(getRemaining, restoreLeakedStock, restoreStock)를
 * 진짜 Redis 위에서 직접 검증한다. 다른 통합 테스트들은 이 메서드들을 간접적으로만
 * 거쳤을 뿐, 각각의 입출력을 명시적으로 확인하지 않아 브랜치 커버리지가 비어 있었다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponRedisProviderIntegrationTest implements MysqlContainerSupport, RedisContainerSupport {

     @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    private CouponRedisProvider couponRedisProvider;

    @Test
    @DisplayName("getRemaining()은 초기화된 재고 값을 정확히 읽어온다")
    void getRemainingReturnsInitializedStock() {
        couponRedisProvider.initializeStock(9001L, 50);

        Long remaining = couponRedisProvider.getRemaining(9001L);

        assertThat(remaining).isEqualTo(50L);
    }

    @Test
    @DisplayName("getRemaining()은 초기화되지 않은 캠페인이면 null을 반환한다")
    void getRemainingReturnsNullForUninitializedCampaign() {
        Long remaining = couponRedisProvider.getRemaining(9999L);

        assertThat(remaining).isNull();
    }

    @Test
    @DisplayName("restoreLeakedStock()은 지정한 양만큼 재고를 되돌린다")
    void restoreLeakedStockIncreasesRemainingByAmount() {
        couponRedisProvider.initializeStock(9002L, 10);
        couponRedisProvider.issue(9002L, "user1");   // 재고 9로 차감

        couponRedisProvider.restoreLeakedStock(9002L, 3);

        assertThat(couponRedisProvider.getRemaining(9002L)).isEqualTo(12L);   // 9 + 3
    }

    @Test
    @DisplayName("restoreStock()은 재고를 1 늘리고 발급 마킹을 삭제해 재발급이 가능해진다")
    void restoreStockIncrementsAndAllowsReissue() {
        couponRedisProvider.initializeStock(9003L, 10);
        long issued = couponRedisProvider.issue(9003L, "user1");
        assertThat(issued).isEqualTo(1L);

        couponRedisProvider.restoreStock(9003L, "user1");

        assertThat(couponRedisProvider.getRemaining(9003L)).isEqualTo(10L);   // 원상복구

        // 마킹이 삭제됐으므로 같은 사용자가 다시 issue해도 성공해야 한다.
        long reissued = couponRedisProvider.issue(9003L, "user1");
        assertThat(reissued).isEqualTo(1L);
    }
}