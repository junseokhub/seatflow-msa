package com.seatflow.coupon.domain;

/**
 * ISSUED   - 발급됨, 아직 사용 안 함
 * RESERVED - 예매에 임시 적용됨 (결제 완료 전, Saga의 SEAT_RELEASED와 비슷한 중간 상태)
 * USED     - 결제 완료로 확정 사용됨
 * RESTORED - 취소로 복원되어 다시 ISSUED와 동등하게 사용 가능
 */
public enum CouponStatus {
    ISSUED,
    RESERVED,
    USED,
    RESTORED
}