package com.seatflow.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 공통 Outbox 발행 스케줄러. OutboxStore 인터페이스에만 의존하므로
 * JPA·Mongo 어느 구현이 주입되든 동일하게 동작한다(특정 DB를 모름).
 *
 * 각 서비스는 자기 OutboxStore 구현(JpaOutboxStore / MongoOutboxStore)을 빈으로 등록하고
 * @EnableScheduling + 이 클래스가 컴포넌트 스캔에 잡히게만 하면 된다.
 *
 *   publish(): PENDING 집기(PUBLISHING 전이) ->Kafka 발행 ->PUBLISHED, 실패 시 백오프 재시도
 *   recover(): PUBLISHING으로 멈춘(전송 중 죽은) 문서를 PENDING으로 복구
 * AtomicBoolean 가드로 한 인스턴스 내 폴링 겹침을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    /** PUBLISHED 후 이 기간이 지난 행만 정리 대상. 재발행·감사 대비로 즉시 삭제하지 않는다. */
    private static final int RETENTION_HOURS = 72;
    /** 한 번의 cleanup 사이클에서 삭제할 최대 행 수(대량 DELETE의 락·복제지연 방지용 상한). */
    private static final int CLEANUP_BATCH_SIZE = 1000;
    /** 한 사이클에서 위 배치를 최대 몇 번 반복할지(폭주 시 무한 점유 방지). */
    private static final int CLEANUP_MAX_LOOPS = 20;

    private final AtomicBoolean publishing = new AtomicBoolean(false);
    private final AtomicBoolean cleaning = new AtomicBoolean(false);

    private final OutboxStore outboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publish() {
        if (!publishing.compareAndSet(false, true)) {
            return;   // 이전 폴링이 아직 도는 중 ->겹치지 않게 스킵
        }
        try {
            List<? extends OutboxMessage> batch = outboxStore.claimPending(BATCH_SIZE);
            for (OutboxMessage message : batch) {
                try {
                    kafkaTemplate.send(message.getEventType(),
                            message.getMessageKey(), message.getPayload()).get();
                    outboxStore.markPublished(message.getId());
                } catch (Exception e) {
                    log.warn("Outbox publish failed: eventId={}, retry scheduled",
                            message.getEventId(), e);
                    outboxStore.markFailedOrRetry(message.getId());
                }
            }
        } finally {
            publishing.set(false);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        outboxStore.recoverStuck(STUCK_THRESHOLD_MINUTES);
    }
    /**
     * 발행 완료된 행 정리. 매체를 모르므로 OutboxStore에 위임한다
     * (JPA는 LIMIT DELETE, Mongo는 deleteMany, 파티션 방식이면 파티션 드롭으로 각자 구현).
     *
     * 대량 DELETE를 한 번에 날리면 락 점유·언두로그 폭증·복제 지연을 유발하므로,
     * CLEANUP_BATCH_SIZE로 끊어서 삭제하고, 더 지울 게 없거나 상한에 도달하면 멈춘다.
     * 발행 직후가 아니라 RETENTION_HOURS 경과분만 지워 재발행·감사 여지를 남긴다.
     * FAILED(재시도 소진) 행은 사람이 봐야 하므로 정리 대상에서 제외한다(구현체 책임).
     *
     * 폴링이 잦을 필요가 없어 1시간 주기. publish와 별도 가드로 겹침만 막는다.
     */
//    @Scheduled(fixedDelay = 3_600_000)
//    public void cleanup() {
//        if (!cleaning.compareAndSet(false, true)) {
//            return;
//        }
//        try {
//            int totalDeleted = 0;
//            for (int loop = 0; loop < CLEANUP_MAX_LOOPS; loop++) {
//                int deleted = outboxStore.deletePublishedBefore(RETENTION_HOURS, CLEANUP_BATCH_SIZE);
//                totalDeleted += deleted;
//                if (deleted < CLEANUP_BATCH_SIZE) {
//                    break;   // 더 지울 게 없음
//                }
//            }
//            if (totalDeleted > 0) {
//                log.info("Outbox cleanup done: deleted={} (retentionHours={})",
//                        totalDeleted, RETENTION_HOURS);
//            }
//        } finally {
//            cleaning.set(false);
//        }
//    }
}