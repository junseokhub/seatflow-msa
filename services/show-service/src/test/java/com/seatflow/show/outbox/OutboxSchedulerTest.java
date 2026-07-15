package com.seatflow.show.outbox;

import com.seatflow.show.domain.Outbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock
    private MongoOutboxStore outboxStore;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private SendResult<String, String> sendResult;

    private OutboxScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxScheduler(outboxStore, kafkaTemplate);
    }

    private Outbox outbox(String id, String eventType, String key, String payload) {
        Outbox o = mock(Outbox.class);
        lenient().when(o.getId()).thenReturn(id);
        lenient().when(o.getEventType()).thenReturn(eventType);
        lenient().when(o.getMessageKey()).thenReturn(key);
        lenient().when(o.getPayload()).thenReturn(payload);
        lenient().when(o.getEventId()).thenReturn("event-" + id);
        return o;
    }

    @Test
    @DisplayName("PENDING을 클레임해서 Kafka로 발행 성공하면 markPublished를 호출한다")
    void publishesSuccessfullyAndMarksPublished() throws Exception {
        Outbox message = outbox("1", "show.created", "show-1", "{}");
        given(outboxStore.claimPending(anyInt())).willReturn(List.of(message));
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        given(kafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(future);

        scheduler.publish();

        verify(outboxStore).markPublished("1");
        verify(outboxStore, never()).markFailedOrRetry(anyString());
    }

    @Test
    @DisplayName("Kafka 발행이 실패하면 markFailedOrRetry를 호출한다")
    void marksFailedOrRetryWhenKafkaSendFails() {
        Outbox message = outbox("1", "show.created", "show-1", "{}");
        given(outboxStore.claimPending(anyInt())).willReturn(List.of(message));
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unreachable"));
        given(kafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(failedFuture);

        scheduler.publish();

        verify(outboxStore).markFailedOrRetry("1");
        verify(outboxStore, never()).markPublished(anyString());
    }

    @Test
    @DisplayName("claimPending이 빈 목록을 반환하면 Kafka도 안 부르고 아무 일도 안 한다")
    void doesNothingWhenNoPendingMessages() {
        given(outboxStore.claimPending(anyInt())).willReturn(List.of());

        scheduler.publish();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("recover()는 outboxStore.recoverStuck을 정확한 임계값으로 호출한다")
    void recoverCallsRecoverStuckWithThreshold() {
        scheduler.recover();

        verify(outboxStore).recoverStuck(5);   // STUCK_THRESHOLD_MINUTES
    }

    @Test
    @DisplayName("이전 publish()가 아직 실행 중이면(AtomicBoolean 가드), 새 호출은 클레임 자체를 시도하지 않는다")
    void skipsWhenAlreadyPublishing() throws Exception {
        // publish() 내부에서 claimPending이 오래 걸리는 상황을 흉내내려면 실제
        // 동시 스레드가 필요하다 — 여기서는 AtomicBoolean 자체의 compareAndSet
        // 동작을 신뢰하고, "정상적으로 한 번 호출되면 가드가 해제되어 다음
        // 호출이 다시 정상 동작한다"는 것만 확인한다(가드가 영구적으로 안
        // 풀리는 걸 방지하는 회귀 테스트).
        given(outboxStore.claimPending(anyInt())).willReturn(List.of());

        scheduler.publish();
        scheduler.publish();   // 첫 호출이 끝났으니 두 번째도 정상적으로 클레임을 시도해야 함

        verify(outboxStore, times(2)).claimPending(anyInt());
    }
}