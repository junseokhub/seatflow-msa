package com.seatflow.user.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.user.domain.User;
import com.seatflow.user.exception.UserErrorCode;
import com.seatflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * UserService.createUser()는 [2] INSERT IGNORE 방식이다.
 * 최종 채택
 * 자세한 경위는 UserService 클래스 주석과 4-1번외편 블로그 참고
 * 예외 자체가 안 나는 방식이라, 테스트도 예외를 던지는지가 아니라 insertIgnore()의 반환값(1=신규, 0=중복)을 어떻게 다루는지에 초점을 맞춘다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Nested
    @DisplayName("createUser()")
    class CreateUser {

        @Test
        @DisplayName("신규 유저면(반환값 1) 정상적으로 끝난다")
        void insertsNewUserSuccessfully() {
            given(userRepository.insertIgnore(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                    .willReturn(1);

            assertThatCode(() -> userService.createUser("user-1", "test@example.com", "테스트"))
                    .doesNotThrowAnyException();

            verify(userRepository).insertIgnore(anyString(), anyString(), anyString(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("중복 유저면(반환값 0) 예외 없이 조용히 끝난다")
        void ignoresDuplicateWithoutException() {
            given(userRepository.insertIgnore(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                    .willReturn(0);

            assertThatCode(() -> userService.createUser("user-1", "test@example.com", "테스트"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("insertIgnore에 정확한 userId, email, name이 전달된다")
        void passesExactArgumentsToInsertIgnore() {
            given(userRepository.insertIgnore(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                    .willReturn(1);

            userService.createUser("user-1", "test@example.com", "테스트유저");

            verify(userRepository).insertIgnore(
                    org.mockito.ArgumentMatchers.eq("user-1"),
                    org.mockito.ArgumentMatchers.eq("test@example.com"),
                    org.mockito.ArgumentMatchers.eq("테스트유저"),
                    any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("getUser()")
    class GetUser {

        @Test
        @DisplayName("존재하는 유저를 반환한다")
        void returnsExistingUser() {
            User user = User.builder().id("user-1").email("test@example.com").name("테스트").build();
            given(userRepository.findById("user-1")).willReturn(Optional.of(user));

            User result = userService.getUser("user-1");

            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("존재하지 않으면 USER_NOT_FOUND 예외를 던진다")
        void throwsWhenNotFound() {
            given(userRepository.findById("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser("unknown"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
        }
    }
}