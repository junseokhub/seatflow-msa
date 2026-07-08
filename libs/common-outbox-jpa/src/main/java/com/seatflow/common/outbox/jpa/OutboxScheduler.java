package com.seatflow.common.outbox.jpa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbox 발행 스케줄러. JPA 전용이라 Outbox 엔티티를 직접 다룬다.
 *   publish(): PENDING 집기(PUBLISHING 전이) → Kafka 발행 → PUBLISHED, 실패 시 백오프 재시도
 *   recover(): PUBLISHING으로 멈춘(전송 중 죽은) 행을 PENDING으로 복구
 * AtomicBoolean 가드로 한 인스턴스 내 폴링 겹침을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    private final AtomicBoolean publishing = new AtomicBoolean(false);

    private final OutboxStore outboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publish() {
        if (!publishing.compareAndSet(false, true)) {
            return;   // 이전 폴링이 아직 도는 중 → 겹치지 않게 스킵
        }
        try {
            List<Outbox> batch = outboxStore.claimPending(BATCH_SIZE);
            for (Outbox message : batch) {
                try {
                    log.info("Outbox Scheduler");
                    log.info("Publishing outbox message: {}", message);
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