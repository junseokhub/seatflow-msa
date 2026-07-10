package com.seatflow.coupon.redis;

import com.seatflow.coupon.domain.CouponCampaign;
import com.seatflow.coupon.repository.CouponCampaignRepository;
import com.seatflow.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CouponRedisProvider의 TTL 방식(issue -> confirmIssued)은 "사용자가 영구히
 * 막히는 문제"는 막지만, 재고(remaining) 카운터 자체의 미세 누수는 못 막는다.
 * issue()에서 DECR(재고 차감)은 TTL 없이 즉시 일어나는데, 그 뒤 MySQL 저장이
 * 예외 없이 조용히 실패하면(서버 다운 등) 그 재고는 영원히 안 돌아온다.
 *
 * 이 스케줄러는 주기적으로 진행 중인(만료되지 않은) 캠페인마다 "Redis 기준
 * 차감량"과 "MySQL 기준 실제 발급량"을 비교해서 그 차이(누수분)를 remaining에
 * 되돌린다.
 *
 * Redis SCAN으로 issued:* 키를 순회하며 MySQL 존재 여부를 하나씩 대조하는 방식
 * 대신, "totalQuantity - remaining(Redis)"과 "실제 발급 수(MySQL count)"를
 * 비교하는 더 단순한 방식을 쓴다. 캠페인당 계산이 O(1)에 가까워 트래픽이 많은
 * 캠페인에서도 SCAN 방식보다 부담이 적다.
 *
 * 진행 중인 PENDING 마킹(아직 TTL이 안 지난 정상적인 발급 시도)까지 누수로
 * 오판하지 않도록, TTL(30초)보다 넉넉한 유예 시간을 두고 판단한다 — 이 스케줄러의
 * 주기(5분)가 이미 TTL보다 훨씬 길어서 별도 유예 로직 없이도 안전하다: 스케줄러가
 * 도는 시점에는 그 사이 발급 시도된 PENDING 마킹이 이미 confirmIssued(성공)
 * 되었거나 TTL로 소멸(실패)했을 것이기 때문이다.
 *
 * 참고: 이 스케줄러가 켜지려면(@EnableScheduling) 서비스 메인 클래스에 해당
 * 애노테이션이 있어야 한다 — 없으면 이 로직 자체가 전혀 실행되지 않는다
 * (auth-service에서 실제로 겪었던 문제와 같은 함정).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStockReconciliationScheduler {

    private final CouponCampaignRepository campaignRepository;
    private final CouponRepository couponRepository;
    private final CouponRedisProvider couponRedisProvider;

    @Scheduled(fixedDelay = 300_000)   // 5분마다. TTL(30초)보다 충분히 길어야 PENDING 오판을 피한다.
    public void reconcile() {
        List<CouponCampaign> activeCampaigns = campaignRepository.findAll().stream()
                .filter(c -> !c.isExpired(LocalDateTime.now()))
                .toList();

        for (CouponCampaign campaign : activeCampaigns) {
            reconcileCampaign(campaign);
        }
    }

    private void reconcileCampaign(CouponCampaign campaign) {
        Long campaignId = campaign.getId();

        Long remaining = couponRedisProvider.getRemaining(campaignId);
        if (remaining == null) {
            // Redis에 재고 키 자체가 없는 상태 — 캠페인 생성 시 initializeStock이
            // 누락됐거나, Redis 데이터가 유실된 경우. 이 스케줄러의 책임 범위
            // 밖이므로 경고만 남기고 넘어간다(재초기화는 별도 운영 조치가 필요).
            log.warn("Coupon campaign {} has no Redis stock key, skip reconciliation", campaignId);
            return;
        }

        long redisIssuedCount = campaign.getTotalQuantity() - remaining;
        long mysqlIssuedCount = couponRepository.countByCampaignId(campaignId);

        long leaked = redisIssuedCount - mysqlIssuedCount;
        if (leaked > 0) {
            log.warn("Coupon stock leak detected: campaignId={}, redisIssued={}, mysqlIssued={}, leaked={}",
                    campaignId, redisIssuedCount, mysqlIssuedCount, leaked);
            couponRedisProvider.restoreLeakedStock(campaignId, leaked);
        } else if (leaked < 0) {
            // Redis 차감량보다 MySQL 발급 수가 더 많은 경우 — 정상적인 흐름에서는
            // 일어날 수 없다(Redis 통과 없이는 MySQL 저장 자체가 안 되므로).
            // 데이터 조작이나 다른 경로로 직접 INSERT된 경우 등 이례적 상황이라
            // 자동 보정하지 않고 경고만 남겨 수동 확인을 유도한다.
            log.error("Coupon stock inconsistency (unexpected): campaignId={}, redisIssued={}, mysqlIssued={}",
                    campaignId, redisIssuedCount, mysqlIssuedCount);
        }
    }
}