package com.seatflow.coupon.service;

import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.coupon.CouponIssuedEvent;
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
 * Redis가 죽으면(CouponRedisProvider가 예외를 던지면) 발급 API 자체를 막는다
 * (Fail-closed) — 선착순 트래픽을 MySQL이 그대로 받으면 커넥션 풀 고갈로 다른
 * API까지 영향을 준다.
 *
 * Kafka가 죽어도 발급은 막히지 않는다 — MySQL 저장과 Outbox 기록이 같은
 * 트랜잭션으로 묶여 있고, Kafka 발행은 OutboxScheduler가 평소처럼 나중에
 * 폴링해서 처리한다. Kafka는 신뢰의 기준점이 아니다.
 */
@Slf4j
@Service("redisCouponService")
@RequiredArgsConstructor
public class DefaultRedisCouponService implements CouponService {

    private static final String SOURCE = "coupon-service";

    private final CouponCampaignRepository campaignRepository;
    private final CouponRepository couponRepository;
    private final CouponRedisProvider couponRedisProvider;
    private final OutboxAppender outboxAppender;

    /**
     * 관리자가 선착순 발급 캠페인을 만든다. 인가(@PreAuthorize)는 컨트롤러에서 처리한다.
     */
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

        // 캠페인 생성 시 Redis 재고도 같이 초기화해야 한다.
        couponRedisProvider.initializeStock(campaign.getId(), totalQuantity);

        return campaign;
    }

    /**
     * 선착순 발급. 1차 판단(재고, 중복)은 Redis Lua 스크립트가 원자적으로 처리하고,
     * 통과한 요청만 MySQL 트랜잭션을 연다 — 재고 소진으로 거절되는 대다수 요청이
     * MySQL 커넥션을 점유하지 않는다.
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

        long result;
        try {
            result = couponRedisProvider.issue(campaignId, userId);
        } catch (Exception e) {
            // Fail-closed. Redis가 없으면 발급 판단 자체를 할 수 없으므로 막는다.
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

        // Redis가 "확정된 당첨"이라고 판단한 요청만 여기 도달한다. MySQL 쓰기는
        // 서로 다른 row(사용자별 쿠폰 레코드)에 대한 단순 insert라 경쟁이 없다.
        // 영구 확정되지 않고, MySQL 저장이 정상적으로 성공했어도 30초 후 마킹이 그냥
        // 사라진다. 그러면 이미 발급받은 사용자의 자리가 "재발급 가능"으로 돌아가서
        // 다른 사람이 같은 재고를 다시 가져갈 수 있는 심각한 버그가 된다.
        try {
            Coupon coupon = Coupon.builder()
                    .campaignId(campaignId)
                    .userId(userId)
                    .discountAmount(campaign.getDiscountAmount())
                    .build();
            couponRepository.save(coupon);
            couponRepository.flush();

            // MySQL 저장 성공 확인 직후 Redis 마킹을 영구 확정한다. 이 줄 전에 예외가
            // 나거나 서버가 죽으면 이 줄이 실행되지 않고, TTL이 알아서 마킹을 지운다
            // (자동 복구) — CouponRedisProvider의 issue/confirmIssued 클래스 주석 참고.
            couponRedisProvider.confirmIssued(campaignId, userId);

            outboxAppender.append(EventTopic.COUPON_ISSUED, SOURCE, userId,
                    new CouponIssuedEvent(coupon.getId(), campaignId, userId));

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

    /**
     * 1단계 — 검증만. reservation이 예매를 만들기 *전에* 호출한다. 상태를 바꾸지
     * 않는다(ISSUED/RESTORED 상태 그대로 둠) — 이 시점엔 아직 reservationId가 없어서
     * "어느 예매에 물려있다"고 확정할 수 없다. 10편에서 결제 금액을 검증만 하고
     * 실제 반영은 결제 성공 후에 했던 것과 같은 2단계 원칙이다.
     */
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

    /**
     * 2단계 — 확정. reservation이 저장되어 reservationId가 생긴 *후에* 호출한다.
     * 이 시점부터 쿠폰이 실제로 그 예매에 물린다(RESERVED).
     */
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

    /** 결제 완료 시 쿠폰 사용을 확정한다. payment.completed 컨슈머가 호출한다. */
    @Override
    @Transactional
    public void confirmUse(Long reservationId) {
        couponRepository.findByReservationId(reservationId).ifPresent(Coupon::confirmUse);
    }

    /**
     * 취소 Saga의 쿠폰 복원 단계. reservationId로 쿠폰을 찾아 복원한다.
     * 쿠폰을 안 쓴 예매의 취소라면(쿠폰이 없으면) 아무것도 안 하고 조용히 끝난다.
     */
    @Override
    @Transactional
    public void restoreByReservation(Long reservationId) {
        couponRepository.findByReservationId(reservationId).ifPresent(Coupon::restore);
    }
}