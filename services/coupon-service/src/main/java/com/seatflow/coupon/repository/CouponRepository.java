package com.seatflow.coupon.repository;

import com.seatflow.coupon.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    boolean existsByCampaignIdAndUserId(Long campaignId, String userId);

    Optional<Coupon> findById(Long id);

    Optional<Coupon> findByIdAndUserId(Long id, String userId);

    List<Coupon> findByUserId(String userId);

    Optional<Coupon> findByReservationId(Long reservationId);

    /** 정합성 점검(CouponStockReconciliationScheduler)이 캠페인별 실제 발급 수를 확인하는 데 쓴다. */
    long countByCampaignId(Long campaignId);
}