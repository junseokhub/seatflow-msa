package com.seatflow.common.event.coupon;

import com.seatflow.common.event.VersionedEvent;

/**
 * common-web(또는 이벤트 정의가 모여있는 공통 모듈)에 추가.
 * 지금은 아무도 구독하는 곳이 없지만(발급 사실을 굳이 다른 서비스가 알아야 할 필요가 아직 없음),
 * Outbox 패턴 자체는 이벤트를 신뢰성 있게 남긴다라는 목적이 구독자 유무와 무관하게 유효하다.
 * 나중에 발급 통계, 알림 등에 쓸 수 있는 이벤트로 미리 정의해둔다.
 */

public record CouponIssuedEvent(
        Long couponId,
        Long campaignId,
        String userId
) implements VersionedEvent {
    @Override
    public String eventVersion() {
        return "1.0";
    }
}