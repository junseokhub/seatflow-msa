package com.seatflow.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxStore {

    private static final int MAX_RETRY = 5;

    private final OutboxRepository outboxRepository;

    @Transactional
    public List<Outbox> claimPending(int limit) {
        List<Outbox> pending = outboxRepository.findPendingForUpdate(limit);
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
            } else {
                outbox.markPendingWithRetry();
            }
        });
    }

    @Transactional
    public void recoverStuck(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        outboxRepository.findStuckPublishing(threshold)
                .forEach(Outbox::markPending);
    }
}