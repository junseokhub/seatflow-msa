package com.seatflow.user.idempotency;

import com.seatflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * [2] INSERT IGNORE 방식은 예외 자체가 나지 않는다 — insertIgnore()가 반환하는
 * 정수(1=신규, 0=중복)로만 결과를 판단한다. 이게 [1]/[3]/[4]와 근본적으로 다른
 * 지점이라, 테스트도 "무엇을 예외로 잡는가"가 아니라 "반환값을 어떻게 다루는가"에
 * 초점을 맞춘다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceInsertIgnoreTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceInsertIgnore service;

    @BeforeEach
    void setUp() {
        service = new UserServiceInsertIgnore(userRepository);
    }

    @Test
    @DisplayName("신규 유저면(반환값 1) 정상적으로 끝난다")
    void insertsNewUserSuccessfully() {
        given(userRepository.insertIgnore(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .willReturn(1);

        assertThatCode(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .doesNotThrowAnyException();

        verify(userRepository).insertIgnore(anyString(), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("중복 유저면(반환값 0) 예외 없이 조용히 끝난다. 애초에 예외 자체가 존재하지 않는 경로")
    void ignoresDuplicateWithoutException() {
        given(userRepository.insertIgnore(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .willReturn(0);

        assertThatCode(() -> service.createUser("user-1", "test@example.com", "테스트"))
                .doesNotThrowAnyException();
    }
}