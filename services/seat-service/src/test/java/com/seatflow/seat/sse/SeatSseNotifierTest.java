package com.seatflow.seat.sse;

import com.seatflow.common.event.seat.SeatStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * emitter.send()가 실패(연결 끊김)했을 때 해당 emitter를 store에서 정리하는지
 * 검증한다. "그냥 메시지를 흘려보내는 것"처럼 보이지만, 실제로는 전송 실패 시
 * 정리하는 분기가 있다 — 처음엔 판단 로직이 없다고 보고 테스트를 건너뛰었는데,
 * 커버리지를 확인하다가 이 분기를 놓쳤다는 걸 알게 됐다.
 */
class SeatSseNotifierTest {

    private SeatEmitterStore seatEmitterStore;
    private SeatSseNotifier notifier;

    @BeforeEach
    void setUp() {
        seatEmitterStore = mock(SeatEmitterStore.class);
        notifier = new SeatSseNotifier(seatEmitterStore);
    }

    @Test
    @DisplayName("전송이 성공하면 emitter는 store에서 제거되지 않는다")
    void successfulSendDoesNotRemoveEmitter() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        given(seatEmitterStore.findByShowId("show-1")).willReturn(java.util.List.of(emitter));

        notifier.onSeatStatusChanged(new SeatStatusChangedEvent("show-1", 1L, "HELD"));

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(seatEmitterStore, never()).remove(anyString(), any());
    }

    @Test
    @DisplayName("전송 중 IOException(연결 끊김)이 나면 해당 emitter를 store에서 제거한다")
    void failedSendRemovesEmitterFromStore() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("connection closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        given(seatEmitterStore.findByShowId("show-1")).willReturn(java.util.List.of(emitter));

        notifier.onSeatStatusChanged(new SeatStatusChangedEvent("show-1", 1L, "HELD"));

        verify(seatEmitterStore).remove("show-1", emitter);
    }

    @Test
    @DisplayName("한 emitter는 실패하고 다른 emitter는 성공하면, 실패한 것만 제거되고 성공한 건 그대로 유지된다")
    void onlyFailedEmitterIsRemovedAmongMultiple() throws IOException {
        SseEmitter failingEmitter = mock(SseEmitter.class);
        SseEmitter workingEmitter = mock(SseEmitter.class);
        doThrow(new IOException("closed")).when(failingEmitter).send(any(SseEmitter.SseEventBuilder.class));
        given(seatEmitterStore.findByShowId("show-1"))
                .willReturn(java.util.List.of(failingEmitter, workingEmitter));

        notifier.onSeatStatusChanged(new SeatStatusChangedEvent("show-1", 1L, "HELD"));

        verify(seatEmitterStore).remove("show-1", failingEmitter);
        verify(seatEmitterStore, never()).remove("show-1", workingEmitter);
    }
}