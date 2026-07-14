package com.seatflow.auth.service;

import com.seatflow.auth.domain.Credentials;
import com.seatflow.auth.exception.AuthErrorCode;
import com.seatflow.auth.jwt.JwtProvider;
import com.seatflow.auth.redis.RedisProvider;
import com.seatflow.auth.repository.CredentialsRepository;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.common.security.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CredentialsRepository credentialsRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RedisProvider redisProvider;
    @Mock
    private OutboxAppender outboxAppender;
    @Mock
    private Claims claims;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();   // 실제 구현 사용 (해싱/매칭 자체를 검증하려면 필요)
        authService = new AuthService(
                credentialsRepository, passwordEncoder, jwtProvider, redisProvider, outboxAppender);
    }

    private Credentials credentials(String email, String rawPassword) {
        return Credentials.builder()
                .userId("user-1")
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.USER)
                .build();
    }

    @Nested
    @DisplayName("signup()")
    class Signup {

        @Test
        @DisplayName("이메일 중복이 없으면 저장하고 user.registered 이벤트를 발행한다")
        void createsCredentialsAndPublishesEvent() {
            given(credentialsRepository.existsByEmail("test@example.com")).willReturn(false);

            authService.signup("test@example.com", "password123", "테스트유저");

            verify(credentialsRepository).save(any(Credentials.class));
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("이메일이 이미 존재하면 DUPLICATE_EMAIL 예외를 던지고 저장/발행하지 않는다")
        void throwsWhenEmailAlreadyExists() {
            given(credentialsRepository.existsByEmail("test@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.signup("test@example.com", "password123", "테스트유저"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.DUPLICATE_EMAIL.getMessage());

            verify(credentialsRepository, never()).save(any());
            verify(outboxAppender, never()).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("비밀번호는 평문이 아니라 해시된 값으로 저장된다")
        void passwordIsStoredAsHash() {
            given(credentialsRepository.existsByEmail("test@example.com")).willReturn(false);
            org.mockito.ArgumentCaptor<Credentials> captor = org.mockito.ArgumentCaptor.forClass(Credentials.class);

            authService.signup("test@example.com", "myPlainPassword", "테스트유저");

            verify(credentialsRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("myPlainPassword");
            assertThat(passwordEncoder.matches("myPlainPassword", captor.getValue().getPasswordHash())).isTrue();
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("계정이 잠겨있으면 비밀번호 확인 전에 ACCOUNT_LOCKED 예외를 던진다")
        void throwsWhenAccountLocked() {
            given(redisProvider.isAccountLocked("test@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.login("test@example.com", "password123"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());

            verify(credentialsRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 INVALID_CREDENTIALS 예외를 던진다")
        void throwsWhenEmailNotFound() {
            given(redisProvider.isAccountLocked("test@example.com")).willReturn(false);
            given(credentialsRepository.findByEmail("test@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("test@example.com", "password123"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.INVALID_CREDENTIALS.getMessage());
        }

        @Test
        @DisplayName("비밀번호가 틀리면 실패 카운트를 늘리고 INVALID_CREDENTIALS 예외를 던진다")
        void incrementsFailCountOnWrongPassword() {
            Credentials creds = credentials("test@example.com", "correctPassword");
            given(redisProvider.isAccountLocked("test@example.com")).willReturn(false);
            given(credentialsRepository.findByEmail("test@example.com")).willReturn(Optional.of(creds));
            given(redisProvider.incrementLoginFail("test@example.com")).willReturn(2L);

            assertThatThrownBy(() -> authService.login("test@example.com", "wrongPassword"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.INVALID_CREDENTIALS.getMessage());

            verify(redisProvider).incrementLoginFail("test@example.com");
        }

        @Test
        @DisplayName("비밀번호 실패가 5회에 도달하면 ACCOUNT_LOCKED 예외로 전환된다")
        void locksAccountAfterFiveFailures() {
            Credentials creds = credentials("test@example.com", "correctPassword");
            given(redisProvider.isAccountLocked("test@example.com")).willReturn(false);
            given(credentialsRepository.findByEmail("test@example.com")).willReturn(Optional.of(creds));
            given(redisProvider.incrementLoginFail("test@example.com")).willReturn(5L);

            assertThatThrownBy(() -> authService.login("test@example.com", "wrongPassword"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.ACCOUNT_LOCKED.getMessage());
        }

        @Test
        @DisplayName("로그인 성공 시 실패 카운트를 리셋하고 토큰을 발급/저장한다")
        void resetsFailCountAndIssuesTokensOnSuccess() {
            Credentials creds = credentials("test@example.com", "correctPassword");
            given(redisProvider.isAccountLocked("test@example.com")).willReturn(false);
            given(credentialsRepository.findByEmail("test@example.com")).willReturn(Optional.of(creds));
            given(jwtProvider.generateAccessToken(any(), any(), any())).willReturn("access-token");
            given(jwtProvider.generateRefreshToken(any())).willReturn("refresh-token");
            given(jwtProvider.getRefreshTokenExpiration()).willReturn(604800000L);

            AuthService.TokenResult result = authService.login("test@example.com", "correctPassword");

            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            verify(redisProvider).resetLoginFail("test@example.com");
            verify(redisProvider).saveRefreshToken(eq(creds.getUserId()), eq("refresh-token"), anyLong());
        }
    }

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("저장된 것과 일치하는 refreshToken이면 새 토큰을 발급한다")
        void issuesNewTokensWhenRefreshTokenMatches() {
            given(jwtProvider.getClaims("valid-refresh")).willReturn(claims);
            given(claims.getSubject()).willReturn("user-1");
            given(redisProvider.getRefreshToken("user-1")).willReturn("valid-refresh");
            given(credentialsRepository.findByUserId("user-1"))
                    .willReturn(Optional.of(credentials("test@example.com", "pw")));
            given(jwtProvider.generateAccessToken(any(), any(), any())).willReturn("new-access");
            given(jwtProvider.generateRefreshToken(any())).willReturn("new-refresh");
            given(jwtProvider.getRefreshTokenExpiration()).willReturn(604800000L);

            AuthService.TokenResult result = authService.refresh("valid-refresh");

            assertThat(result.accessToken()).isEqualTo("new-access");
        }

        @Test
        @DisplayName("저장된 토큰과 다르면(탈취/재사용 의심) 저장된 토큰을 삭제하고 INVALID_TOKEN 예외를 던진다")
        void deletesStoredTokenAndThrowsWhenMismatch() {
            given(jwtProvider.getClaims("stale-refresh")).willReturn(claims);
            given(claims.getSubject()).willReturn("user-1");
            given(redisProvider.getRefreshToken("user-1")).willReturn("different-refresh");

            assertThatThrownBy(() -> authService.refresh("stale-refresh"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.INVALID_TOKEN.getMessage());

            verify(redisProvider).deleteRefreshToken("user-1");
        }

        @Test
        @DisplayName("Redis에 저장된 토큰 자체가 없으면(만료 등) INVALID_TOKEN 예외를 던진다")
        void throwsWhenNoStoredToken() {
            given(jwtProvider.getClaims("refresh")).willReturn(claims);
            given(claims.getSubject()).willReturn("user-1");
            given(redisProvider.getRefreshToken("user-1")).willReturn(null);

            assertThatThrownBy(() -> authService.refresh("refresh"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.INVALID_TOKEN.getMessage());
        }

        @Test
        @DisplayName("토큰은 유효하고 Redis도 일치하는데, Credentials가 DB에서 삭제된 예외적 상황이면 INVALID_TOKEN 예외를 던진다")
        void throwsWhenCredentialsDeletedAfterTokenIssued() {
            given(jwtProvider.getClaims("valid-refresh")).willReturn(claims);
            given(claims.getSubject()).willReturn("deleted-user");
            given(redisProvider.getRefreshToken("deleted-user")).willReturn("valid-refresh");
            given(credentialsRepository.findByUserId("deleted-user")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("valid-refresh"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.INVALID_TOKEN.getMessage());
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("남은 유효시간이 있으면 accessToken을 블랙리스트에 등록하고 refreshToken을 삭제한다")
        void blacklistsAccessTokenAndDeletesRefreshToken() {
            given(jwtProvider.getRemaining("access")).willReturn(60000L);
            given(jwtProvider.getClaims("refresh")).willReturn(claims);
            given(claims.getSubject()).willReturn("user-1");

            authService.logout("access", "refresh");

            verify(redisProvider).addBlacklist("access", 60000L);
            verify(redisProvider).deleteRefreshToken("user-1");
        }

        @Test
        @DisplayName("이미 만료된 accessToken(남은 시간 0 이하)은 블랙리스트에 등록하지 않는다")
        void doesNotBlacklistAlreadyExpiredToken() {
            given(jwtProvider.getRemaining("access")).willReturn(-1000L);
            given(jwtProvider.getClaims("refresh")).willReturn(claims);
            given(claims.getSubject()).willReturn("user-1");

            authService.logout("access", "refresh");

            verify(redisProvider, never()).addBlacklist(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("블랙리스트에 없고 유효한 토큰이면 클레임 정보를 반환한다")
        void returnsClaimsForValidToken() {
            given(redisProvider.isBlacklisted("access")).willReturn(false);
            given(jwtProvider.getClaims("access")).willReturn(claims);
            given(claims.getSubject()).willReturn("user-1");
            given(claims.get("email", String.class)).willReturn("test@example.com");
            given(claims.get("role", String.class)).willReturn("USER");

            AuthService.ValidateResult result = authService.validate("access");

            assertThat(result.userId()).isEqualTo("user-1");
            assertThat(result.email()).isEqualTo("test@example.com");
            assertThat(result.role()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("블랙리스트에 있는 토큰은(로그아웃된 토큰) INVALID_TOKEN 예외를 던진다")
        void throwsWhenTokenIsBlacklisted() {
            given(redisProvider.isBlacklisted("access")).willReturn(true);

            assertThatThrownBy(() -> authService.validate("access"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AuthErrorCode.INVALID_TOKEN.getMessage());

            verify(jwtProvider, never()).getClaims(anyString());
        }
    }
}