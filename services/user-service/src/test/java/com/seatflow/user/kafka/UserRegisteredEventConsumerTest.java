package com.seatflow.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 컨슈머는 멱등 처리를 하지 않는다.
 * UserService.createUser()의 noRollbackFor 전략이 그 책임을 전담하므로, 컨슈머는 파싱과 위임만 한다.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

    @Mock
    private UserService userService;

    private UserRegisteredEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new UserRegisteredEventConsumer(userService, realObjectMapper);
    }

    @Test
    @DisplayName("정상 메시지는 파싱되어 createUser에 정확한 값으로 위임된다")
    void validMessageDelegatesToCreateUser() {
        String validMessage = """
                { "payload": { "userId": "user-1", "email": "test@example.com", "name": "테스트유저" } }
                """;

        consumer.consume(validMessage);

        verify(userService).createUser("user-1", "test@example.com", "테스트유저");
    }

    @Test
    @DisplayName("깨진 메시지는 IllegalStateException을 던지고(DLQ 대상) 서비스는 호출하지 않는다")
    void malformedMessageThrowsForDlq() {
        assertThrows(IllegalStateException.class, () -> consumer.consume("not json"));

        verify(userService, never()).createUser(anyString(), anyString(), anyString());
    }
}