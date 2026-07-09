package com.seatflow.coupon.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 발급 캠페인. 관리자가 "이 정액 할인 쿠폰을 N장 선착순으로 뿌린다"를 등록하는
 * 단위다. 실제 사용자에게 발급되는 Coupon은 이 캠페인의 재고(totalQuantity)를
 * 소진하며 생성된다.
 *
 * 동시성 제어: issuedQuantity 증가는 여러 사용자가 동시에 요청할 수 있는 지점이라
 * 9편(좌석 정원 제어)과 같은 문제다. 원자적 UPDATE(조건부 증가)로 재고를 넘지 않게
 * 막는다 — CouponCampaignRepository의 increaseIssuedQuantity 참고.
 */
@Entity
@Table(name = "coupon_campaigns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private int totalQuantity;

    /** 원자적 UPDATE로만 증가시킨다. 엔티티를 읽어 +1 하고 save하는 방식은 동시 요청에서
     * 재고를 초과 발급할 위험이 있다(9편에서 좌석 정원을 이렇게 잘못 다뤘다가 고친 것과 같다). */
    @Column(nullable = false)
    private int issuedQuantity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    @Builder
    private CouponCampaign(String name, BigDecimal discountAmount, int totalQuantity, LocalDateTime expiresAt) {
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isSoldOut() {
        return issuedQuantity >= totalQuantity;
    }
}