package com.seatflow.coupon.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.domain.CouponStatus;
import com.seatflow.coupon.exception.CouponErrorCode;
import com.seatflow.coupon.repository.CouponCampaignRepository;
import com.seatflow.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 별도 인프라(Redis) 없이 MySQL만으로 선착순 발급의 동시성을 제어하는 버전.
 * 트래픽이 크지 않을 때는 이것만으로 충분히 안전하다. RedisCouponService와
 * 나란히 남겨 비교하기 위한 학습 기록이다.
 */
@Slf4j
@Service("mysqlCouponService")
@RequiredArgsConstructor
public class DefaultMysqlCouponService implements CouponService {

    private final CouponCampaignRepository campaignRepository;
    private final CouponRepository couponRepository;

    /**
     * 관리자가 선착순 발급 캠페인을 만든다. 인가(@PreAuthorize)는 컨트롤러에서 처리한다.
     */
    @Override
    @Transactional
    public CouponCampaign createCampaign(String name, BigDecimal discountAmount,
                                         int totalQuantity, LocalDateTime expiresAt) {
        return campaignRepository.save(CouponCampaign.builder()
                .name(name)
                .discountAmount(discountAmount)
                .totalQuantity(totalQuantity)
                .expiresAt(expiresAt)
                .build());
    }

    /**
     * 선착순 쿠폰 발급. 동시성 제어의 핵심은 두 단계다.
     *
     * 1. 재고 원자적 차감(increaseIssuedQuantity) — WHERE issuedQuantity < totalQuantity
     *    조건의 UPDATE 자체가 락 역할을 한다. 결과가 0이면 재고 소진.
     * 2. 1인 1매 제약 — DB unique 제약(campaignId, userId)에 맡긴다. 동시에 같은
     *    사용자가 두 번 요청해도(광클) 하나만 성공하고 나머지는 DataIntegrityViolationException.
     *
     * 두 단계 다 "먼저 읽고 판단 후 쓰기" 방식이 아니라 "쓰기 자체가 조건이자 판단"이라는
     * 점이 같다 — 9편에서 좌석 정원 원자적 UPDATE, 8편 근처에서 회원가입 1062 처리로
     * 반복해서 써온 원칙이다.
     */
    @Override
    @Transactional
    public Coupon issueCoupon(Long campaignId, String userId) {
        CouponCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(
                        CouponErrorCode.CAMPAIGN_NOT_FOUND.getStatus().value(),
                        CouponErrorCode.CAMPAIGN_NOT_FOUND.getMessage()));

        if (campaign.isExpired(LocalDateTime.now())) {
            throw new BusinessException(
                    CouponErrorCode.CAMPAIGN_EXPIRED.getStatus().value(),
                    CouponErrorCode.CAMPAIGN_EXPIRED.getMessage());
        }

        int updated = campaignRepository.increaseIssuedQuantity(campaignId);
        if (updated == 0) {
            throw new BusinessException(
                    CouponErrorCode.CAMPAIGN_SOLD_OUT.getStatus().value(),
                    CouponErrorCode.CAMPAIGN_SOLD_OUT.getMessage());
        }

        try {
            Coupon coupon = Coupon.builder()
                    .campaignId(campaignId)
                    .userId(userId)
                    .discountAmount(campaign.getDiscountAmount())
                    .build();
            couponRepository.save(coupon);
            couponRepository.flush();   // unique 제약 위반을 여기서 즉시 확인 (커밋 시점까지 안 미룸)
            return coupon;
        } catch (DataIntegrityViolationException e) {
            // 재고는 이미 차감했는데 유저 유니크 제약에 걸림 = 이 유저는 이미 발급받은 상태.
            // 재고를 되돌리지 않는다 — 이미 다른 요청이 이 재고로 정상 발급받았을 뿐,
            // "실패한 이 요청" 때문에 차감된 게 아니다(increaseIssuedQuantity는 그 자체로
            // 이 요청의 몫을 성공적으로 차지한 것이고, 이후 insert 실패는 별개의 제약이다).
            throw new BusinessException(
                    CouponErrorCode.ALREADY_ISSUED.getStatus().value(),
                    CouponErrorCode.ALREADY_ISSUED.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponCampaign> getCampaigns() {
        return campaignRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public CouponCampaign getCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        CouponErrorCode.CAMPAIGN_NOT_FOUND.getStatus().value(),
                        CouponErrorCode.CAMPAIGN_NOT_FOUND.getMessage()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Coupon> getUserCoupons(String userId) {
        return couponRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Coupon getCoupon(Long id, String userId) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        CouponErrorCode.COUPON_NOT_FOUND.getStatus().value(),
                        CouponErrorCode.COUPON_NOT_FOUND.getMessage()));
        if (!coupon.getUserId().equals(userId)) {
            throw new BusinessException(
                    CouponErrorCode.COUPON_NOT_OWNED.getStatus().value(),
                    CouponErrorCode.COUPON_NOT_OWNED.getMessage());
        }
        return coupon;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal validateForReservation(Long couponId, String userId) {
        Coupon coupon = getCoupon(couponId, userId);
        if (coupon.getStatus() != CouponStatus.ISSUED && coupon.getStatus() != CouponStatus.RESTORED) {
            throw new BusinessException(
                    CouponErrorCode.COUPON_NOT_USABLE.getStatus().value(),
                    CouponErrorCode.COUPON_NOT_USABLE.getMessage());
        }
        return coupon.getDiscountAmount();
    }

    @Override
    @Transactional
    public void confirmForReservation(Long couponId, String userId, Long reservationId) {
        Coupon coupon = getCoupon(couponId, userId);
        try {
            coupon.reserve(reservationId);
        } catch (IllegalStateException e) {
            throw new BusinessException(
                    CouponErrorCode.COUPON_NOT_USABLE.getStatus().value(),
                    CouponErrorCode.COUPON_NOT_USABLE.getMessage());
        }
    }

    @Override
    @Transactional
    public void confirmUse(Long reservationId) {
        couponRepository.findByReservationId(reservationId).ifPresent(Coupon::confirmUse);
    }

    @Override
    @Transactional
    public void restoreByReservation(Long reservationId) {
        couponRepository.findByReservationId(reservationId).ifPresent(Coupon::restore);
    }

//    @Override
//    public Coupon issueCouponOutbox(Long campaignId, String userId) {
//        return null;
//    }
}