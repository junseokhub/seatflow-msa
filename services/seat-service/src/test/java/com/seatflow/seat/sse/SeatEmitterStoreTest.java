package com.seatflow.seat.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeatEmitterStoreTest {

    private final SeatEmitterStore store = new SeatEmitterStore();

    @Test
    @DisplayName("create()는 새 SseEmitter를 만들어 해당 showId 목록에 등록한다")
    void createRegistersEmitterUnderShowId() {
        SseEmitter emitter = store.create("show-1");

        assertThat(emitter).isNotNull();
        assertThat(store.findByShowId("show-1")).containsExactly(emitter);
    }

    @Test
    @DisplayName("같은 showId로 여러 번 create하면 전부 같은 목록에 쌓인다")
    void multipleCreatesAccumulateUnderSameShowId() {
        SseEmitter emitter1 = store.create("show-1");
        SseEmitter emitter2 = store.create("show-1");

        List<SseEmitter> emitters = store.findByShowId("show-1");
        assertThat(emitters).containsExactlyInAnyOrder(emitter1, emitter2);
    }

    @Test
    @DisplayName("서로 다른 showId는 독립적인 목록을 가진다")
    void differentShowIdsHaveIndependentLists() {
        SseEmitter emitter1 = store.create("show-1");
        SseEmitter emitter2 = store.create("show-2");

        assertThat(store.findByShowId("show-1")).containsExactly(emitter1);
        assertThat(store.findByShowId("show-2")).containsExactly(emitter2);
    }

    @Test
    @DisplayName("등록된 적 없는 showId는 빈 리스트를 반환한다")
    void findByShowIdReturnsEmptyListForUnknownShow() {
        assertThat(store.findByShowId("unknown-show")).isEmpty();
    }

    @Test
    @DisplayName("remove()는 해당 emitter만 목록에서 제거한다")
    void removeDeletesOnlySpecifiedEmitter() {
        SseEmitter emitter1 = store.create("show-1");
        SseEmitter emitter2 = store.create("show-1");

        store.remove("show-1", emitter1);

        assertThat(store.findByShowId("show-1")).containsExactly(emitter2);
    }

    @Test
    @DisplayName("존재하지 않는 showId에 대한 remove()는 예외 없이 조용히 무시된다")
    void removeOnUnknownShowIdDoesNothing() {
        SseEmitter someEmitter = new SseEmitter();

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> store.remove("unknown-show", someEmitter));
    }

    // emitter.onCompletion/onTimeout/onError 콜백이 실제로 remove()를 호출하는지는
    // 여기서 검증하지 않는다. SseEmitter의 콜백은 서블릿 컨테이너의 비동기 디스패치
    // 메커니즘에 의존해 트리거되는데, 순수 단위 테스트 환경(서블릿 컨테이너 없음)
    // 에서는 emitter.complete()를 호출해도 콜백이 실행된다는 보장이 없어 테스트가
    // 불안정(flaky)해진다 — 처음에 이 검증을 시도했다가 정확히 이 이유로 실패를
    // 겪었다. 콜백이 실제로 트리거되는지는 SeatController를 실제 서블릿 컨테이너
    // 위에서 띄우는 통합/E2E 테스트의 영역이며, create()가 onCompletion 등록
    // 자체를 빠뜨리지 않았는지는 코드 리뷰로 확인하는 게 더 적절하다.
}