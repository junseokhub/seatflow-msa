package com.seatflow.user.idempotency;

import com.seatflow.user.domain.User;
import com.seatflow.user.inbox.IdempotentEventProcessor;
import com.seatflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * UserServiceInboxPattern은 IdempotentEventProcessor에게 실제 판단(선점 성공 여부)을 완전히 위임하므로,
 * 이 서비스 자체의 단위 테스트는 IdempotentEventProcessor에 정확한 인자로 위임하는가만 확인하면 충분하다.
 * 선점 성공/실패에 따른 분기 로직 자체는 이미 IdempotentEventProcessorTest에서 검증했으므로 여기서 중복 검증하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceInboxPatternTest {

    @Mock
    private IdempotentEventProcessor idempotentEventProcessor;
    @Mock
    private UserRepository userRepository;

    private UserServiceInboxPattern service;

    @BeforeEach
    void setUp() {
        service = new UserServiceInboxPattern(idempotentEventProcessor, userRepository);
    }

    @Test
    @DisplayName("createUser()는 IdempotentEventProcessor.process()에 정확한 consumerGroup/eventId/eventType을 전달한다")
    void delegatesToIdempotentEventProcessorWithCorrectArguments() {
        service.createUser("event-1", "user-1", "test@example.com", "테스트");

        verify(idempotentEventProcessor).process(
                org.mockito.ArgumentMatchers.eq("user-service"),
                org.mockito.ArgumentMatchers.eq("event-1"),
                org.mockito.ArgumentMatchers.eq("user.registered"),
                any(Runnable.class));
    }

    @Test
    @DisplayName("전달된 businessLogic(Runnable)이 실행되면 실제로 User가 저장된다")
    void businessLogicSavesUserWhenExecuted() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        service.createUser("event-1", "user-1", "test@example.com", "테스트");

        verify(idempotentEventProcessor).process(
                anyString(), anyString(), anyString(), runnableCaptor.capture());

        // IdempotentEventProcessor가 실제로 businessLogic.run()을 호출했다고
        // 가정하고, 그 Runnable을 여기서 직접 실행해 안에서 userRepository.save()가
        // 정확히 불리는지 확인한다.
        runnableCaptor.getValue().run();

        verify(userRepository).save(any(User.class));
    }
}