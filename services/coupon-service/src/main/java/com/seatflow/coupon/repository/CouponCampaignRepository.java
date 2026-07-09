package com.seatflow.coupon.repository;

import com.seatflow.coupon.domain.CouponCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, Long> {

    /**
     * 재고를 원자적으로 1 증가시킨다. WHERE 절에 issuedQuantity < totalQuantity 조건을
     * 걸어, 이 UPDATE 자체가 "재고가 남아있을 때만 성공"하는 동시성 제어 역할을 한다.
     * 9편에서 좌석 정원을 이렇게 원자적 UPDATE로 다뤘던 것과 같은 패턴이다.
     *
     * @return 실제로 갱신된 row 수. 1이면 발급 성공, 0이면 이미 매진(다른 요청이 먼저
     *         마지막 재고를 가져갔다는 뜻)이라 서비스 계층에서 SOLD_OUT으로 처리한다.
     */
    @Modifying
    @Query("UPDATE CouponCampaign c SET c.issuedQuantity = c.issuedQuantity + 1 " +
            "WHERE c.id = :campaignId AND c.issuedQuantity < c.totalQuantity")
    int increaseIssuedQuantity(@Param("campaignId") Long campaignId);
}