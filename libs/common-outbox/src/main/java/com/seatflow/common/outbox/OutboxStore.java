package com.seatflow.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxStore {

    private static final int MAX_RETRY = 10;                       // 캡 백오프 기준 ≈ 13분 버팀
    private static final Duration BACKOFF_BASE = Duration.ofSeconds(1);
    private static final Duration BACKOFF_CAP = Duration.ofMinutes(5);

    private final OutboxRepository outboxRepository;

    @Transactional
    public List<Outbox> claimPending(int limit) {
        List<Outbox> pending = outboxRepository.findPendingForUpdate(LocalDateTime.now(), limit);
        pending.forEach(Outbox::markPublishing);
        return pending;
    }

    @Transactional
    public void markPublished(Long id) {
        outboxRepository.findById(id).ifPresent(Outbox::markPublished);
    }

    @Transactional
    public void markFailedOrRetry(Long id) {
        outboxRepository.findById(id).ifPresent(outbox -> {
            if (outbox.isExceededRetry(MAX_RETRY)) {
                outbox.markFailed();
                log.warn("Outbox moved to FAILED (manual redrive required): eventId={}, eventType={}, retryCount={}",
                        outbox.getEventId(), outbox.getEventType(), outbox.getRetryCount());
            } else {
                outbox.markRetry(nextRetryAt(outbox.getRetryCount() + 1));
            }
        });
    }

    @Transactional
    public void recoverStuck(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        outboxRepository.findStuckPublishing(threshold)
                .forEach(Outbox::markPendingNow);
    }

    /** FAILED 행을 PENDING으로 재투입. 운영자/관리 트리거에서 호출(자동 스케줄 X — poison 무한루프 방지) */
    @Transactional
    public int redriveFailed(int limit) {
        List<Outbox> failed = outboxRepository.findFailedForUpdate(limit);
        failed.forEach(Outbox::markRedrive);
        if (!failed.isEmpty()) {
            log.info("Outbox redrive: {} FAILED rows requeued", failed.size());
        }
        return failed.size();
    }

    /** AWS equal jitter: delay/2 + rand(0, delay/2) */
    private LocalDateTime nextRetryAt(int retryCount) {
        long expMs = (long) (BACKOFF_BASE.toMillis() * Math.pow(2, retryCount - 1));
        long delayMs = Math.min(expMs, BACKOFF_CAP.toMillis());
        long half = delayMs / 2;
        long jittered = half + ThreadLocalRandom.current().nextLong(half + 1);
        return LocalDateTime.now().plus(Duration.ofMillis(jittered));
    }
}