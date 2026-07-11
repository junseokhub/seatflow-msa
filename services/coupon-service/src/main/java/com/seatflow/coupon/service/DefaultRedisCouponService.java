package com.seatflow.coupon.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.domain.CouponStatus;
import com.seatflow.coupon.exception.CouponErrorCode;
import com.seatflow.coupon.redis.CouponRedisProvider;
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
 * 선착순 쿠폰 발급의 동시성 제어는 Redis(1차 판단, 원자적)와 MySQL(확정 저장,
 * Outbox로 신뢰성 있게 이벤트 발행)로 역할이 나뉜다.
 *
 * 트랜잭션 경계 설계: issueCoupon() 자체에는 @Transactional을 걸지 않는다. Redis
 * 호출(issue, confirmIssued)은 외부 네트워크 왕복이 있는 I/O라, 이걸 JPA 트랜잭션
 * 안에 넣으면 그 왕복 시간 내내 DB 커넥션을 붙잡고 있게 된다. 선착순 이벤트처럼
 * 동시 요청이 몰리는 상황에서 DB 커넥션 풀이 이 불필요하게 긴 점유로 고갈되고,
 * 나머지 요청들이 커넥션을 기다리며 줄줄이 블로킹된다(통합 테스트로 동시 요청을
 * 재현하고서야 드러난 문제였다 — Mock 단위 테스트에서는 안 보인다).
 *
 * DB 쓰기는 CouponPersister라는 별도 빈으로 분리했다. 같은 클래스 안에서
 * this.persistCoupon()처럼 자기 자신을 호출하면 스프링 AOP 프록시가 그 호출을
 * 가로채지 못해 @Transactional이 무시되는 self-invocation 문제가 있어, 트랜잭션
 * 경계가 필요한 부분을 아예 다른 빈으로 떼어냈다.
 *
 * Redis가 죽으면(CouponRedisProvider가 예외를 던지면) 발급 API 자체를 막는다
 * (Fail-closed). Kafka가 죽어도 발급은 막히지 않는다 — MySQL 저장과 Outbox 기록이
 * 같은 트랜잭션으로 묶여 있고, Kafka 발행은 OutboxScheduler가 나중에 처리한다.
 */
@Slf4j
@Service("redisCouponService")
@RequiredArgsConstructor
public class DefaultRedisCouponService implements CouponService {

    private static final String SOURCE = "coupon-service";

    private final CouponCampaignRepository campaignRepository;
    private final CouponRepository couponRepository;
    private final CouponRedisProvider couponRedisProvider;
    private final CouponPersister couponPersister;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional
    public CouponCampaign createCampaign(String name, BigDecimal discountAmount,
                                         int totalQuantity, LocalDateTime expiresAt) {
        CouponCampaign campaign = campaignRepository.save(CouponCampaign.builder()
                .name(name)
                .discountAmount(discountAmount)
                .totalQuantity(totalQuantity)
                .expiresAt(expiresAt)
                .build());

        couponRedisProvider.initializeStock(campaign.getId(), totalQuantity);

        return campaign;
    }

    /**
     * 트랜잭션 없이 진입한다. DB 커넥션은 couponPersister.persist() 호출 구간에서만
     * (프록시를 거쳐) 짧게 열린다 — Redis 호출 두 번(issue, confirmIssued)은 그
     * 바깥에서 이루어진다.
     */
    @Override
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

        long result;
        try {
            result = couponRedisProvider.issue(campaignId, userId);
        } catch (Exception e) {
            log.error("Coupon Redis unavailable: campaignId={}", campaignId, e);
            throw new BusinessException(
                    CouponErrorCode.REDIS_UNAVAILABLE.getStatus().value(),
                    CouponErrorCode.REDIS_UNAVAILABLE.getMessage());
        }

        if (result == 0L) {
            throw new BusinessException(
                    CouponErrorCode.CAMPAIGN_SOLD_OUT.getStatus().value(),
                    CouponErrorCode.CAMPAIGN_SOLD_OUT.getMessage());
        }
        if (result == -1L) {
            throw new BusinessException(
                    CouponErrorCode.ALREADY_ISSUED.getStatus().value(),
                    CouponErrorCode.ALREADY_ISSUED.getMessage());
        }

        try {
            Coupon coupon = couponPersister.persist(campaignId, userId, campaign.getDiscountAmount());

            // MySQL 저장 성공 확인 후(커넥션은 이미 반납된 상태) Redis 마킹을
            // 영구 확정한다.
            couponRedisProvider.confirmIssued(campaignId, userId);

//            outboxAppender.append(EventTopic.COUPON_ISSUED, SOURCE, userId,
//                    new CouponIssuedEvent(coupon.getId(), campaignId, userId));

            return coupon;
        } catch (DataIntegrityViolationException e) {
            log.error("Unexpected duplicate on MySQL insert despite Redis guard: " +
                    "campaignId={}, userId={}", campaignId, userId, e);
            couponRedisProvider.restoreStock(campaignId, userId);
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
}