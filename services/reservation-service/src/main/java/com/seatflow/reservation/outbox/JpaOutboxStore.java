package com.seatflow.reservation.outbox;

import com.seatflow.reservation.domain.Outbox;
import com.seatflow.reservation.repository.OutboxRepository;
import com.seatflow.common.outbox.OutboxBackoff;
import com.seatflow.common.outbox.OutboxMessage;
import com.seatflow.common.outbox.OutboxStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
public class JpaOutboxStore implements OutboxStore {

    private final OutboxRepository outboxRepository;

    @Override
    @Transactional
    public List<? extends OutboxMessage> claimPending(int limit) {
        List<Outbox> pending = outboxRepository.findPendingForUpdate(LocalDateTime.now(), limit);
        pending.forEach(Outbox::markPublishing);
        return pending;
    }

    @Override
    @Transactional
    public int deletePublishedBefore(int retentionHours, int limit) {
        Instant threshold = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        return outboxRepository.deletePublishedBefore(threshold, limit);
    }

    @Override
    @Transactional
    public void markPublished(String id) {
        outboxRepository.findById(Long.valueOf(id)).ifPresent(Outbox::markPublished);
    }

    @Override
    @Transactional
    public void markFailedOrRetry(String id) {
        outboxRepository.findById(Long.valueOf(id)).ifPresent(outbox -> {
            if (OutboxBackoff.isExceeded(outbox.getRetryCount())) {
                outbox.markFailed();
            } else {
                outbox.markRetry(OutboxBackoff.nextRetryAt(outbox.getRetryCount() + 1));
            }
        });
    }

    @Override
    @Transactional
    public void recoverStuck(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        outboxRepository.findStuckPublishing(threshold).forEach(Outbox::markPendingNow);
    }

    @Override
    @Transactional
    public long redriveFailed(int limit) {
        List<Outbox> failed = outboxRepository.findFailedForUpdate(limit);
        failed.forEach(Outbox::markRedrive);
        return failed.size();
    }
}