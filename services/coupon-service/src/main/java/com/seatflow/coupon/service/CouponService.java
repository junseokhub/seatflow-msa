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

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponCampaignRepository campaignRepository;
    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public List<CouponCampaign> getCampaigns() {
        return campaignRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CouponCampaign getCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        CouponErrorCode.CAMPAIGN_NOT_FOUND.getStatus().value(),
                        CouponErrorCode.CAMPAIGN_NOT_FOUND.getMessage()));
    }

    /**
     * 관리자가 선착순 발급 캠페인을 만든다. 인가(@PreAuthorize)는 컨트롤러에서 처리한다.
     */
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

    @Transactional(readOnly = true)
    public List<Coupon> getUserCoupons(String userId) {
        return couponRepository.findByUserId(userId);
    }

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


    /**
     * reservation-service가 예매 생성 시점에 동기 호출한다(10편 결제 금액 검증과 같은 이유 —
     * 그 순간 정확한 할인액이 필요하다). 검증 통과 시 쿠폰을 RESERVED로 임시 적용한다.
     */
    @Transactional
    public Coupon reserveForReservation(Long couponId, String userId, Long reservationId) {
        Coupon coupon = getCoupon(couponId, userId);
        try {
            coupon.reserve(reservationId);
        } catch (IllegalStateException e) {
            throw new BusinessException(
                    CouponErrorCode.COUPON_NOT_USABLE.getStatus().value(),
                    CouponErrorCode.COUPON_NOT_USABLE.getMessage());
        }
        return coupon;
    }

    /** 결제 완료 시 쿠폰 사용을 확정한다. payment.completed 컨슈머가 호출한다. */
    @Transactional
    public void confirmUse(Long reservationId) {
        couponRepository.findByReservationId(reservationId).ifPresent(Coupon::confirmUse);
    }

    /**
     * 취소 Saga의 쿠폰 복원 단계. reservationId로 쿠폰을 찾아 복원한다.
     * 쿠폰을 안 쓴 예매의 취소라면(쿠폰이 없으면) 아무것도 안 하고 조용히 끝난다.
     */
    @Transactional
    public void restoreByReservation(Long reservationId) {
        couponRepository.findByReservationId(reservationId).ifPresent(Coupon::restore);
    }
}