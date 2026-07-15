package com.seatflow.user.idempotency;

import com.seatflow.user.domain.User;
import com.seatflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * [3] REQUIRES_NEW 방식의 분기 로직 자체는 [1]과 동일하다(예외를 잡아서 삼킨다).
 * 다만 Mock 단위 테스트로는 진짜 새 트랜잭션이 열렸는지, 그 트랜잭션만 롤백됐는지는 검증할 수 없다.
 * 그건 IdempotencyStrategyComparisonIntegrationTest (진짜 DB)의 영역이다.
 * 여기서는 [1]과 마찬가지로 예외 삼킴/전파 분기만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceRequiresNewTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceRequiresNew service;

    @BeforeEach
    void setUp() {
        service = new UserServiceRequiresNew(userRepository);
    }

    @Test
    @DisplayName("정상 저장이면 예외 없이 끝난다")
    void savesSuccessfully() {
        given(userRepository.saveAndFlush(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .doesNotThrowAnyException();

        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("unique 제약 위반이 나도 예외를 삼키고 조용히 끝난다")
    void silentlyCatchesUniqueConstraintViolation() {
        SQLException sqlEx = new SQLException("Duplicate entry", "23000", 1062);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate", sqlEx));

        assertThatCode(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .doesNotThrowAnyException();
    }
}