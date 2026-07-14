package com.seatflow.user.idempotency;

import com.seatflow.user.domain.User;
import com.seatflow.user.repository.UserRepository;
import com.seatflow.user.service.UserServiceNoRollbackFor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * [4] noRollbackFor 방식의 분기 로직만 검증한다.
 *
 * 중요 — 이 테스트가 통과한다고 해서 이 방식이 안전하다는 뜻이 아니다.
 * Mock은 "saveAndFlush()가 예외를 던진다"까지만 흉내 낼 뿐, 그 예외가 나는
 * 순간 진짜 Spring 트랜잭션 매니저가 트랜잭션을 rollback-only로 마킹하는
 * 것까지는 재현하지 못한다. 실제로 이 방식은 진짜 동시 요청 상황에서
 * UnexpectedRollbackException을 냈고(운영 UserService가 이 방식이었을 때
 * 발견), 그 문제는 이 단위 테스트가 전부 통과한 채로도 숨어 있었다.
 *
 * 진짜 안전성 검증은 IdempotencyStrategyComparisonIntegrationTest의
 * noRollbackForFailsUnderRealConcurrency(진짜 DB + 진짜 트랜잭션)에서
 * 이뤄진다 — 이 클래스는 그 실패를 재현하는 목적으로 일부러 남겨져 있다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceNoRollbackForTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceNoRollbackFor service;

    @BeforeEach
    void setUp() {
        service = new UserServiceNoRollbackFor(userRepository);
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
    @DisplayName("unique 제약 위반(1062)이면 예외를 삼키고 조용히 끝난다 — Mock 레벨에서는 문제없어 보인다")
    void silentlyCatchesUniqueConstraintViolation() {
        SQLException sqlEx = new SQLException("Duplicate entry", "23000", 1062);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate", sqlEx));

        // Mock 레벨에서는 이렇게 "잘 잡아서 넘기는 것"처럼 보인다. 하지만 진짜
        // Spring 트랜잭션 매니저가 없는 이 테스트 환경에서는, catch 이전에
        // 트랜잭션이 rollback-only로 마킹되는 부작용 자체가 시뮬레이션되지
        // 않는다 — 그래서 이 테스트는 통과하지만 운영에서는 문제가 났었다.
        assertThatCode(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1062가 아닌 다른 정합성 위반(예: NOT NULL)은 다시 던져 롤백을 유도한다")
    void rethrowsNonDuplicateIntegrityViolation() {
        SQLException sqlEx = new SQLException("Column cannot be null", "23000", 1048);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("not null violation", sqlEx));

        assertThatThrownBy(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("SQLException이 아닌 다른 원인의 DataIntegrityViolationException도 그대로 다시 던진다")
    void rethrowsWhenRootCauseIsNotSqlException() {
        DataIntegrityViolationException nonSqlCause =
                new DataIntegrityViolationException("some other cause", new RuntimeException("not a SQL exception"));
        given(userRepository.saveAndFlush(any(User.class))).willThrow(nonSqlCause);

        assertThatThrownBy(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}