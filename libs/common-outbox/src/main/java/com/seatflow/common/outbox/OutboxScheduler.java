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
 *   publish(): PENDING 집기(PUBLISHING 전이) → Kafka 발행 → PUBLISHED, 실패 시 백오프 재시도
 *   recover(): PUBLISHING으로 멈춘(전송 중 죽은) 문서를 PENDING으로 복구
 * AtomicBoolean 가드로 한 인스턴스 내 폴링 겹침을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    private final OutboxStore outboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final AtomicBoolean publishing = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 1000)
    public void publish() {
        if (!publishing.compareAndSet(false, true)) {
            return;   // 이전 폴링이 아직 도는 중 → 겹치지 않게 스킵
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
}