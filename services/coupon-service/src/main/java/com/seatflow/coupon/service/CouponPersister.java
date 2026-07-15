package com.seatflow.coupon.service;

import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.coupon.CouponIssuedEvent;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 쿠폰 저장 전용, 트랜잭션 경계를 이 클래스로 분리했다. 같은 클래스(RedisCouponService)
 * 안에서 this.persistCoupon(...)처럼 자기 자신을 호출하면 스프링 AOP 프록시가
 * 그 호출을 가로채지 못해 @Transactional이 무시된다(self-invocation 문제, 스프링의
 * 잘 알려진 함정). 별도 빈으로 분리해 RedisCouponService가 이 빈을 주입받아 호출하면,
 * 프록시를 거쳐서 호출되므로 트랜잭션이 정상적으로 적용된다.
 */
@Component
@RequiredArgsConstructor
public class CouponPersister {
    private static final String SOURCE = "coupon-service";
    private final CouponRepository couponRepository;
    private final OutboxAppender outboxAppender;

    @Transactional
    public Coupon persist(Long campaignId, String userId, BigDecimal discountAmount) {
        Coupon coupon = Coupon.builder()
                .campaignId(campaignId)
                .userId(userId)
                .discountAmount(discountAmount)
                .build();
        couponRepository.save(coupon);
        couponRepository.flush();

        // Coupon 저장과 같은 트랜잭션 안에서 Outbox에 기록한다.
        // 이 트랜잭션이 커밋되면 둘 다 성공한 것이고, 롤백되면 둘 다(Coupon insert, outbox insert) 같이 롤백된다.
        // 부분 성공이 없다.
        outboxAppender.append(EventTopic.COUPON_ISSUED, SOURCE, userId,
                new CouponIssuedEvent(coupon.getId(), campaignId, userId));

        return coupon;
    }
}