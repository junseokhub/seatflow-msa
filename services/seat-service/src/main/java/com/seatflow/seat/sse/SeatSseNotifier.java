package com.seatflow.seat.sse;

import com.seatflow.seat.event.SeatStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;


// Spring이벤트 듣고 -> 로컬 Store에 브로드캐스트
@Component
@RequiredArgsConstructor
public class SeatSseNotifier {

    private final SeatEmitterStore seatEmitterStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatStatusChanged(SeatStatusChangedEvent event) {
        SeatUpdateMessage message = SeatUpdateMessage.from(event);
        for (SseEmitter emitter : seatEmitterStore.findByShowId(event.showId())) {
            try {
                emitter.send(SseEmitter.event().name("seat-update").data(message));
            } catch (IOException e) {
                seatEmitterStore.remove(event.showId(), emitter);
            }
        }
    }
}