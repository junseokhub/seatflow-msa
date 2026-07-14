package com.seatflow.user.service;

import com.seatflow.user.domain.User;
import com.seatflow.user.idempotency.UserServiceSaveAndCatch;
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

@ExtendWith(MockitoExtension.class)
class UserServiceSaveAndCatchTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceSaveAndCatch service;

    @BeforeEach
    void setUp() {
        service = new UserServiceSaveAndCatch(userRepository);
    }

    @Test
    @DisplayName("정상 저장이면 예외 없이 끝난다")
    void savesSuccessfully() {
        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .doesNotThrowAnyException();

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("unique 제약 위반이 나도 예외를 삼키고 조용히 끝난다 (이 방식의 핵심 동작)")
    void silentlyCatchesUniqueConstraintViolation() {
        SQLException sqlEx = new SQLException("Duplicate entry", "23000", 1062);
        given(userRepository.save(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate", sqlEx));

        // 이 방식은 위반 원인을 세분화하지 않고 DataIntegrityViolationException이면
        // 무조건 삼킨다 — [4](noRollbackFor)와 달리 1062인지 별도로 확인하지 않는
        // 것도 이 방식의 특징(더 단순하지만 더 거칠다)이다.
        assertThatCode(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .doesNotThrowAnyException();
    }
}