package com.seatflow.user.inbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * IdempotentEventProcessor는 선점(insertIfAbsent) 성공 시에만 비즈니스 로직을 실행한다는 게 핵심이다.
 * 이게 다른 세 방식과 다른 지점(별도 처리 이력을 먼저 확정한 뒤에만 실제 작업을 한다)이라,
 * 여기서는 businessLogic(Runnable)이 실행됐는지 여부 자체를 직접 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class IdempotentEventProcessorTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private IdempotentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new IdempotentEventProcessor(processedEventRepository);
    }

    @Test
    @DisplayName("선점(insertIfAbsent)이 성공(1)하면 businessLogic이 실행된다")
    void runsBusinessLogicWhenPreemptionSucceeds() {
        given(processedEventRepository.insertIfAbsent(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .willReturn(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        processor.process("user-service", "event-1", "user.registered", () -> executed.set(true));

        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("선점이 실패(0, 이미 처리됨)하면 businessLogic은 실행되지 않는다")
    void doesNotRunBusinessLogicWhenPreemptionFails() {
        given(processedEventRepository.insertIfAbsent(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .willReturn(0);
        AtomicBoolean executed = new AtomicBoolean(false);

        processor.process("user-service", "event-1", "user.registered", () -> executed.set(true));

        assertThat(executed.get()).isFalse();
    }

    @Test
    @DisplayName("insertIfAbsent에는 정확히 전달한 consumerGroup, eventId, eventType이 그대로 넘어간다")
    void passesExactArgumentsToInsertIfAbsent() {
        given(processedEventRepository.insertIfAbsent(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .willReturn(1);

        processor.process("user-service", "event-42", "user.registered", () -> {});

        verify(processedEventRepository).insertIfAbsent(
                org.mockito.ArgumentMatchers.eq("user-service"),
                org.mockito.ArgumentMatchers.eq("event-42"),
                org.mockito.ArgumentMatchers.eq("user.registered"),
                any(LocalDateTime.class));
    }
}