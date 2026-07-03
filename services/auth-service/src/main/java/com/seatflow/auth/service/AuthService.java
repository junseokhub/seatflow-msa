package com.seatflow.auth.service;

import com.seatflow.auth.domain.Credentials;
import com.seatflow.auth.exception.AuthErrorCode;
import com.seatflow.auth.jwt.JwtProvider;
import com.seatflow.auth.redis.RedisProvider;
import com.seatflow.auth.repository.CredentialsRepository;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.user.UserRegisteredEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.common.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CredentialsRepository credentialsRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisProvider redisProvider;
    private final OutboxAppender outboxAppender;

    @Transactional
    public void signup(String email, String password, String name) {
        if (credentialsRepository.existsByEmail(email)) {
            throw new BusinessException(
                    AuthErrorCode.DUPLICATE_EMAIL.getStatus().value(),
                    AuthErrorCode.DUPLICATE_EMAIL.getMessage()
            );
        }

        String userId = UUID.randomUUID().toString();
        String passwordHash = passwordEncoder.encode(password);

        Credentials credentials = Credentials.builder()
                .userId(userId)
                .email(email)
                .passwordHash(passwordHash)
                .role(Role.USER)
                .build();

        credentialsRepository.save(credentials);

        // Outbox에 적재 (같은 트랜잭션 → dual-write 제거)
        outboxAppender.append(
                EventTopic.USER_REGISTERED,
                "auth-service",
                userId,
                new UserRegisteredEvent(userId, email, name));
    }

    @Transactional(readOnly = true)
    public TokenResult login(String email, String password) {
        if (redisProvider.isAccountLocked(email)) {
            throw new BusinessException(
                    AuthErrorCode.ACCOUNT_LOCKED.getStatus().value(),
                    AuthErrorCode.ACCOUNT_LOCKED.getMessage()
            );
        }

        Credentials credentials = credentialsRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(
                        AuthErrorCode.INVALID_CREDENTIALS.getStatus().value(),
                        AuthErrorCode.INVALID_CREDENTIALS.getMessage()
                ));

        if (!passwordEncoder.matches(password, credentials.getPasswordHash())) {
            long failCount = redisProvider.incrementLoginFail(email);
            if (failCount >= 5) {
                throw new BusinessException(
                        AuthErrorCode.ACCOUNT_LOCKED.getStatus().value(),
                        AuthErrorCode.ACCOUNT_LOCKED.getMessage()
                );
            }
            throw new BusinessException(
                    AuthErrorCode.INVALID_CREDENTIALS.getStatus().value(),
                    AuthErrorCode.INVALID_CREDENTIALS.getMessage()
            );
        }

        redisProvider.resetLoginFail(email);

        String accessToken = jwtProvider.generateAccessToken(credentials.getUserId(), email, credentials.getRole());
        String refreshToken = jwtProvider.generateRefreshToken(credentials.getUserId());

        redisProvider.saveRefreshToken(credentials.getUserId(), refreshToken,
                jwtProvider.getRefreshTokenExpiration());

        return new TokenResult(accessToken, refreshToken);
    }

    public TokenResult refresh(String refreshToken) {
        var claims = jwtProvider.getClaims(refreshToken);
        String userId = claims.getSubject();

        String stored = redisProvider.getRefreshToken(userId);
        if (stored == null || !stored.equals(refreshToken)) {
            redisProvider.deleteRefreshToken(userId);
            throw new BusinessException(
                    AuthErrorCode.INVALID_TOKEN.getStatus().value(),
                    AuthErrorCode.INVALID_TOKEN.getMessage()
            );
        }

        Credentials credentials = credentialsRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        AuthErrorCode.INVALID_TOKEN.getStatus().value(),
                        AuthErrorCode.INVALID_TOKEN.getMessage()));

        String newAccessToken = jwtProvider.generateAccessToken(
                userId, credentials.getEmail(), credentials.getRole());
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        redisProvider.saveRefreshToken(userId, newRefreshToken,
                jwtProvider.getRefreshTokenExpiration());

        return new TokenResult(newAccessToken, newRefreshToken);
    }

    public void logout(String accessToken, String refreshToken) {
        long remaining = jwtProvider.getRemaining(accessToken);
        if (remaining > 0) {
            redisProvider.addBlacklist(accessToken, remaining);
        }
        String userId = jwtProvider.getClaims(refreshToken).getSubject();
        redisProvider.deleteRefreshToken(userId);
    }

    public ValidateResult validate(String accessToken) {
        if (redisProvider.isBlacklisted(accessToken)) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_TOKEN.getStatus().value(),
                    AuthErrorCode.INVALID_TOKEN.getMessage()
            );
        }

        var claims = jwtProvider.getClaims(accessToken);
        return new ValidateResult(claims.getSubject(), (String) claims.get("email"), claims.get("role", Role.class));
    }

    public record TokenResult(String accessToken, String refreshToken) {}
    public record ValidateResult(String userId, String email, Role role) {}
}