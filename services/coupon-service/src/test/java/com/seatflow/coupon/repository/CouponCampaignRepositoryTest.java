package com.seatflow.coupon.repository;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.coupon.domain.CouponCampaign;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MysqlCouponService(9편 방식, 원자적 UPDATE)가 쓰는 increaseIssuedQuantity()를
 * 직접 검증한다.
 *
 * increaseIssuedQuantity()에는 @Modifying(clearAutomatically = true)를 붙여야
 * 한다 — @Modifying 쿼리는 영속성 컨텍스트(1차 캐시)를 거치지 않고 DB에 직접
 * UPDATE를 날리는데, 캐시는 그 사실을 모른 채 예전 값을 계속 들고 있는다.
 * clearAutomatically = true는 이 UPDATE 직후 1차 캐시를 자동으로 비워서, 이어지는
 * findById() 등이 캐시된 옛날 값이 아니라 진짜 DB 값을 다시 읽어오게 한다. 이
 * 옵션이 없으면 "UPDATE는 분명 성공(반환값 1)했는데 재조회한 객체의 필드 값은
 * 그대로"인 혼란스러운 상황을 겪는다 — 실제로 겪었다.
 *
 * 동시성 검증(여러 스레드가 진짜로 경쟁하는 시나리오)은 여기 포함하지 않는다.
 * @DataJpaTest는 테스트 메서드 전체를 하나의 트랜잭션으로 감싸고 끝나면 롤백하는
 * 구조라, 그 트랜잭션은 테스트를 실행하는 메인 스레드에만 걸린다 — 새로 만든
 * 스레드는 이 트랜잭션 컨텍스트를 모르는 별도 스레드라 동시성 재현에 적합하지
 * 않다. 그 시나리오는 MysqlCouponCampaignConcurrencyIntegrationTest
 * (@SpringBootTest 기반)에서 다룬다.
 */
@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponCampaignRepositoryTest implements MysqlContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private CouponCampaignRepository campaignRepository;

    @Test
    @DisplayName("increaseIssuedQuantity()는 재고가 남아있으면 1건 갱신하고 issuedQuantity를 늘린다")
    void increaseIssuedQuantitySucceedsWhenStockRemains() {
        CouponCampaign campaign = campaignRepository.save(CouponCampaign.builder()
                .name("테스트").discountAmount(BigDecimal.valueOf(1000))
                .totalQuantity(10).expiresAt(null).build());
        campaignRepository.flush();

        int updated = campaignRepository.increaseIssuedQuantity(campaign.getId());

        assertThat(updated).isEqualTo(1);

        // clearAutomatically = true 덕분에 별도로 entityManager.clear()를 부르지
        // 않아도 findById()가 진짜 DB 값을 읽어온다.
        CouponCampaign reloaded = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(reloaded.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("increaseIssuedQuantity()는 재고가 이미 소진됐으면 0건 갱신하고 issuedQuantity를 안 늘린다")
    void increaseIssuedQuantityFailsWhenStockExhausted() {
        CouponCampaign campaign = campaignRepository.save(CouponCampaign.builder()
                .name("테스트").discountAmount(BigDecimal.valueOf(1000))
                .totalQuantity(1).expiresAt(null).build());
        campaignRepository.flush();
        campaignRepository.increaseIssuedQuantity(campaign.getId());   // 재고 소진(1/1)

        int updated = campaignRepository.increaseIssuedQuantity(campaign.getId());   // 한 번 더 시도

        assertThat(updated).isEqualTo(0);
        CouponCampaign reloaded = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(reloaded.getIssuedQuantity()).isEqualTo(1);   // 더 안 늘어남
    }
}