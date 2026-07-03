package com.seatflow.common.outbox.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OutboxStore의 JPA 구현. 빈 등록은 CommonOutboxAutoConfiguration의 @Bean으로 한다.
 * 스케줄러가 실제로 쓰는 동작(집기·발행표시·재시도·복구)만 노출한다.
 */
@RequiredArgsConstructor
public class OutboxStore {

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
            if (OutboxBackoff.isExceeded(outbox.getRetryCount())) {
                outbox.markFailed();
            } else {
                outbox.markRetry(OutboxBackoff.nextRetryAt(outbox.getRetryCount() + 1));
            }
        });
    }

    @Transactional
    public void recoverStuck(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        outboxRepository.findStuckPublishing(threshold)
                .forEach(Outbox::markPendingNow);
    }
}