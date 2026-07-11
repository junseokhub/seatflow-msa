package com.seatflow.coupon.repository;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.coupon.domain.Coupon;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponRepositoryTest implements MysqlContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private CouponRepository couponRepository;

    private Coupon coupon(Long campaignId, String userId) {
        return Coupon.builder()
                .campaignId(campaignId)
                .userId(userId)
                .discountAmount(BigDecimal.valueOf(5000))
                .build();
    }

    @Test
    @DisplayName("existsByCampaignIdAndUserId()는 정확히 그 조합이 있을 때만 true를 반환한다")
    void existsByCampaignIdAndUserIdChecksExactCombination() {
        couponRepository.save(coupon(1L, "user1"));

        assertThat(couponRepository.existsByCampaignIdAndUserId(1L, "user1")).isTrue();
        assertThat(couponRepository.existsByCampaignIdAndUserId(1L, "user2")).isFalse();   // 다른 유저
        assertThat(couponRepository.existsByCampaignIdAndUserId(2L, "user1")).isFalse();   // 다른 캠페인
    }

    @Test
    @DisplayName("findByIdAndUserId()는 본인 소유일 때만 조회되고 타인 소유면 빈 Optional을 반환한다")
    void findByIdAndUserIdReturnsOnlyWhenOwnedByUser() {
        Coupon saved = couponRepository.save(coupon(1L, "owner"));

        Optional<Coupon> ownedResult = couponRepository.findByIdAndUserId(saved.getId(), "owner");
        Optional<Coupon> notOwnedResult = couponRepository.findByIdAndUserId(saved.getId(), "someone-else");

        assertThat(ownedResult).isPresent();
        assertThat(notOwnedResult).isEmpty();
    }

    @Test
    @DisplayName("findByUserId()는 해당 유저의 쿠폰만 전부 반환한다")
    void findByUserIdReturnsAllCouponsForThatUser() {
        couponRepository.save(coupon(1L, "user1"));
        couponRepository.save(coupon(2L, "user1"));
        couponRepository.save(coupon(1L, "user2"));   // 다른 유저

        List<Coupon> result = couponRepository.findByUserId("user1");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.getUserId().equals("user1"));
    }

    @Test
    @DisplayName("findByReservationId()는 그 예매에 물린 쿠폰을 정확히 찾는다")
    void findByReservationIdFindsAttachedCoupon() {
        Coupon saved = couponRepository.save(coupon(1L, "user1"));
        saved.reserve(100L);
        couponRepository.save(saved);

        Optional<Coupon> result = couponRepository.findByReservationId(100L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("findByReservationId()는 어떤 쿠폰도 그 예매에 안 물려 있으면 빈 Optional을 반환한다")
    void findByReservationIdReturnsEmptyWhenNoCouponAttached() {
        Optional<Coupon> result = couponRepository.findByReservationId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("countByCampaignId()는 해당 캠페인으로 발급된 쿠폰 수를 정확히 센다")
    void countByCampaignIdCountsOnlyMatchingCampaign() {
        couponRepository.save(coupon(1L, "user1"));
        couponRepository.save(coupon(1L, "user2"));
        couponRepository.save(coupon(2L, "user1"));   // 다른 캠페인

        long count = couponRepository.countByCampaignId(1L);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 (campaignId, userId) 조합으로 중복 저장하면 unique 제약 위반이 난다 (1인 1매)")
    void duplicateCampaignUserCombinationViolatesUniqueConstraint() {
        couponRepository.save(coupon(1L, "user1"));
        couponRepository.flush();

        Coupon duplicate = coupon(1L, "user1");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> {
                    couponRepository.save(duplicate);
                    couponRepository.flush();
                });
    }
}