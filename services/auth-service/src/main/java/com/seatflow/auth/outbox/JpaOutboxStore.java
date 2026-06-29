package com.seatflow.auth.outbox;

import com.seatflow.auth.domain.Outbox;
import com.seatflow.auth.repository.OutboxRepository;
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

/**
 * OutboxStore의 JPA 구현(어댑터). 기존 common-outbox의 OutboxStore 로직을 이동.
 * 동시성 제어는 FOR UPDATE SKIP LOCKED(OutboxRepository 쿼리)로 한다.
 * 백오프/한계 정책은 공통 OutboxBackoff를 사용해 Mongo 구현과 동일하게 맞춘다.
 * 실패/격리 흔적은 outbox 상태(status, retryCount)로 남는다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
public class JpaOutboxStore implements OutboxStore {

    private final OutboxRepository outboxRepository;

    @Override
    @Transactional
    public List<? extends OutboxMessage> claimPending(int limit) {
        List<Outbox> pending = outboxRepository.findPendingForUpdate(LocalDateTime.now(), limit);
        pending.forEach(Outbox::markPublishing);   // 더티체킹으로 PUBLISHING 반영
        return pending;   // List<Outbox> 그대로 반환 (복사 없음). 반환 타입이 와일드카드라 OK
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
        outboxRepository.findStuckPublishing(threshold)
                .forEach(Outbox::markPendingNow);
    }

    @Override
    @Transactional
    public long redriveFailed(int limit) {
        List<Outbox> failed = outboxRepository.findFailedForUpdate(limit);
        failed.forEach(Outbox::markRedrive);
        return failed.size();
    }
}