package com.seatflow.coupon.service;

import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 도메인의 전체 유스케이스를 정의한다.
 * 두 구현체(RedisCouponService, MysqlCouponService)는 발급(issueCoupon)의 동시성 제어 방식만 다르고
 * 나머지 로직(조회, 결제 연동)은 동일하다.
 * 실제로 갈아끼우며 쓰려는 목적이 아니라, 선착순 재고 경쟁을 Redis로 풀 때와 MySQL만으로 풀 때 각각 완결된 형태로 남겨 비교하기 위한 학습 기록이다.
 * 운영에서는 둘 중 하나만 @Component로 활성화해서 쓰자.
 */
public interface CouponService {

    CouponCampaign createCampaign(String name, BigDecimal discountAmount,
                                  int totalQuantity, LocalDateTime expiresAt);

    Coupon issueCoupon(Long campaignId, String userId);

    List<CouponCampaign> getCampaigns();

    CouponCampaign getCampaign(Long id);

    List<Coupon> getUserCoupons(String userId);

    Coupon getCoupon(Long id, String userId);

    BigDecimal validateForReservation(Long couponId, String userId);

    void confirmForReservation(Long couponId, String userId, Long reservationId);

    void confirmUse(Long reservationId);

    void restoreByReservation(Long reservationId);
}