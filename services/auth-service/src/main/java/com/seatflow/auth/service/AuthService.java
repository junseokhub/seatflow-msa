package com.seatflow.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.auth.domain.Credentials;
import com.seatflow.auth.domain.Outbox;
import com.seatflow.auth.exception.AuthErrorCode;
import com.seatflow.auth.jwt.JwtProvider;
import com.seatflow.auth.redis.RedisProvider;
import com.seatflow.auth.repository.CredentialsRepository;
import com.seatflow.auth.repository.OutboxRepository;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.user.UserRegisteredEvent;
import com.seatflow.common.exception.BusinessException;
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
    private final OutboxRepository outboxRepository;
    private final ObjectMapper kafkaObjectMapper;

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
                .build();

        credentialsRepository.save(credentials);

        // Kafka 직접 발행 대신 Outbox에 저장 (같은 트랜잭션)
        try {
            EventEnvelope<UserRegisteredEvent> event = EventEnvelope.of(
                    EventTopic.USER_REGISTERED,
                    "auth-service",
                    new UserRegisteredEvent(userId, email, name)
            );
            String payload = kafkaObjectMapper.writeValueAsString(event);

            outboxRepository.save(Outbox.builder()
                    .eventId(event.eventId())
                    .eventType(EventTopic.USER_REGISTERED)
                    .messageKey(userId)
                    .payload(payload)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(
                    AuthErrorCode.INTERNAL_ERROR.getStatus().value(),
                    AuthErrorCode.INTERNAL_ERROR.getMessage()
            );
        }
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