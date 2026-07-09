package com.seatflow.coupon.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정액 할인 쿠폰. CouponCampaign(선착순 발급 캠페인)의 재고를 소진하며 사용자가
 * 직접 발급받는다. 한 캠페인당 사용자 1인 1매로 관리한다(campaignId + userId 유니크).
 *
 * 상태 전이: ISSUED -> RESERVED -> USED (정상 사용)
 *           RESERVED -> RESTORED -> (재사용 가능, ISSUED와 동일하게 취급) (취소로 복원)
 * 모든 전이 메서드는 멱등하게 설계해 중복 이벤트에도 안전하다.
 */
@Entity
@Table(name = "coupons", uniqueConstraints = @UniqueConstraint(columnNames = {"campaignId", "userId"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    /** 이 쿠폰이 지금 어느 예매에 물려있는지. RESERVED/USED 상태일 때만 값이 있다. */
    private Long reservationId;

    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;

    @Builder
    private Coupon(Long campaignId, String userId, BigDecimal discountAmount) {
        this.campaignId = campaignId;
        this.userId = userId;
        this.discountAmount = discountAmount;
        this.status = CouponStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }

    /**
     * 예매에 쿠폰을 적용한다(임시 적용, 결제 전). ISSUED이거나 RESTORED일 때만 가능하다.
     * 이미 이 예매에 적용돼 있으면(재시도) 멱등하게 무시한다.
     */
    public void reserve(Long reservationId) {
        if (this.status == CouponStatus.RESERVED && reservationId.equals(this.reservationId)) {
            return;   // 멱등
        }
        if (this.status != CouponStatus.ISSUED && this.status != CouponStatus.RESTORED) {
            throw new IllegalStateException("사용할 수 없는 쿠폰 상태: " + this.status);
        }
        this.status = CouponStatus.RESERVED;
        this.reservationId = reservationId;
    }

    /** 결제 완료로 쿠폰 사용을 확정한다. */
    public void confirmUse() {
        if (this.status == CouponStatus.USED) {
            return;   // 멱등
        }
        if (this.status != CouponStatus.RESERVED) {
            throw new IllegalStateException("확정할 수 없는 쿠폰 상태: " + this.status);
        }
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 취소로 쿠폰을 복원한다. Saga 보상 단계에서 호출된다.
     * RESERVED/USED 어느 쪽이든 복원 가능하다(결제 완료 후 취소도 있으므로).
     */
    public void restore() {
        if (this.status == CouponStatus.RESTORED) {
            return;   // 멱등
        }
        this.status = CouponStatus.RESTORED;
        this.reservationId = null;
    }
}