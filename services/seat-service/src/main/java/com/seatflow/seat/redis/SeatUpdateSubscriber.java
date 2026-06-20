package com.seatflow.seat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.seat.event.SeatStatusChangedEvent;
import com.seatflow.seat.sse.SeatEmitterStore;
import com.seatflow.seat.sse.SeatUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatUpdateSubscriber implements MessageListener {

    private final SeatEmitterStore seatEmitterStore;
    private final ObjectMapper objectMapper;

    // Redis 채널에서 받아 → 이 pod에 붙은 연결들에 push
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SeatStatusChangedEvent event =
                    objectMapper.readValue(message.getBody(), SeatStatusChangedEvent.class);

            SeatUpdateMessage payload = SeatUpdateMessage.from(event);
            for (SseEmitter emitter : seatEmitterStore.findByShowId(event.showId())) {
                try {
                    emitter.send(SseEmitter.event().name("seat-update").data(payload));
                } catch (IOException e) {
                    seatEmitterStore.remove(event.showId(), emitter);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle seat update from Redis", e);
        }
    }
}