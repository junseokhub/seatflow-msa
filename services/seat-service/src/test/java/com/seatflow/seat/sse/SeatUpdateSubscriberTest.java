package com.seatflow.seat.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.seat.SeatStatusChangedEvent;
import com.seatflow.seat.redis.SeatUpdateSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Redis pub/sub 메시지를 받아 SSE로 전달하는 컴포넌트. 처음엔 "그냥 받아서 흘려
 * 보내는 것"이라 skip했지만, 실제로는 (1) 메시지 파싱 실패, (2) 개별 emitter 전송
 * 실패 두 개의 실패 분기가 있다 — SeatSseNotifier와 거의 같은 구조다.
 */
class SeatUpdateSubscriberTest {

    private SeatEmitterStore seatEmitterStore;
    private ObjectMapper objectMapper;
    private SeatUpdateSubscriber subscriber;

    @BeforeEach
    void setUp() {
        seatEmitterStore = mock(SeatEmitterStore.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        subscriber = new SeatUpdateSubscriber(seatEmitterStore, objectMapper);
    }

    private Message mockMessage(byte[] body) {
        Message message = mock(Message.class);
        given(message.getBody()).willReturn(body);
        return message;
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 해당 showId의 모든 emitter에 전송된다")
    void validMessageIsSentToAllEmitters() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        given(seatEmitterStore.findByShowId("show-1")).willReturn(java.util.List.of(emitter));

        String json = objectMapper.writeValueAsString(new SeatStatusChangedEvent("show-1", 1L, "HELD"));
        subscriber.onMessage(mockMessage(json.getBytes()), null);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("깨진 메시지는 예외를 삼키고 로그만 남긴다 (구독자 자체가 죽지 않음)")
    void malformedMessageIsSwallowedNotThrown() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> subscriber.onMessage(mockMessage("not json".getBytes()), null));

        verify(seatEmitterStore, never()).findByShowId(anyString());
    }

    @Test
    @DisplayName("emitter 전송 실패(IOException)면 해당 emitter를 store에서 제거한다")
    void failedEmitterSendRemovesFromStore() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        given(seatEmitterStore.findByShowId("show-1")).willReturn(java.util.List.of(emitter));

        String json = objectMapper.writeValueAsString(new SeatStatusChangedEvent("show-1", 1L, "HELD"));
        subscriber.onMessage(mockMessage(json.getBytes()), null);

        verify(seatEmitterStore).remove("show-1", emitter);
    }
}