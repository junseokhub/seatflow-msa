package com.seatflow.user.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentEventProcessor {

    private final ProcessedEventRepository processedEventRepository;

    /** inbox 선기록 + 비즈니스 로직을 한 트랜잭션으로 묶어 멱등 처리 */
    @Transactional
    public void process(String consumerGroup, String eventId, String eventType, Runnable businessLogic) {
        int inserted = processedEventRepository.insertIfAbsent(
                consumerGroup, eventId, eventType, LocalDateTime.now());

        if (inserted == 0) {
            log.info("Duplicate event skipped: group={}, eventId={}", consumerGroup, eventId);
            return;
        }

        businessLogic.run();
    }
}