package com.seatflow.seat.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SeatEmitterStore {

    private final Map<String, List<SseEmitter>> store = new ConcurrentHashMap<>();
    private static final long TIMEOUT = 30 * 60 * 1000L;

    // 연결 생성 + 등록 + 생애주기 정리까지
    public SseEmitter create(String showId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        store.computeIfAbsent(showId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(showId, emitter));
        emitter.onTimeout(() -> remove(showId, emitter));
        emitter.onError(e -> remove(showId, emitter));
        return emitter;
    }

    public List<SseEmitter> findByShowId(String showId) {
        return store.getOrDefault(showId, List.of());
    }

    public void remove(String showId, SseEmitter emitter) {
        List<SseEmitter> list = store.get(showId);
        if (list != null) list.remove(emitter);
    }
}