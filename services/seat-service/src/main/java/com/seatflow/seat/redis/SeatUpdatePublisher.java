package com.seatflow.seat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.seat.SeatStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatUpdatePublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 커밋된 좌석 변경을 Redis 채널로 발행 ->모든 pod이 받는다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(SeatStatusChangedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(SseRedisConfig.SEAT_UPDATE_CHANNEL, json);
        } catch (Exception e) {
            log.error("Failed to publish seat update: {}", event, e);
        }
    }
}