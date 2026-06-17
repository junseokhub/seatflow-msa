package com.seatflow.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxStore outboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void publish() {
        List<Outbox> claimed = outboxStore.claimPending(100);

        for (Outbox outbox : claimed) {
            try {
                kafkaTemplate.send(
                        outbox.getEventType(),
                        outbox.getMessageKey(),
                        outbox.getPayload()
                ).get();

                outboxStore.markPublished(outbox.getId());
                log.info("Outbox published: eventId={}, eventType={}",
                        outbox.getEventId(), outbox.getEventType());

            } catch (Exception e) {
                outboxStore.markFailedOrRetry(outbox.getId());
                log.error("Outbox publish failed: eventId={}, error={}",
                        outbox.getEventId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStuck() {
        outboxStore.recoverStuck(5);
    }
}