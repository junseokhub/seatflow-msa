package com.seatflow.auth.jwt;

import com.seatflow.auth.config.properties.JwtProperties;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.security.JwtValidator;
import com.seatflow.common.security.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * JwtProvider는 실제 RSA 개인키로 서명하는 로직을 갖고 있어, 순수 Mock으로는
 * "진짜 서명이 되는가"를 검증할 수 없다. 테스트 전용 RSA 키 페어를 생성해서
 * JwtProperties에 채우고, 실제로 서명된 JWT가 파싱 가능한 형태인지까지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class JwtProviderTest {

    @Mock
    private JwtValidator jwtValidator;
    @Mock
    private Claims claims;

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(privateKeyPem);
        jwtProperties.setAccessTokenExpiration(3600000L);
        jwtProperties.setRefreshTokenExpiration(604800000L);

        jwtProvider = new JwtProvider(jwtProperties, jwtValidator);
    }

    @Test
    @DisplayName("generateAccessToken()은 파싱 가능한 형태의 JWT 문자열을 생성한다 (3개 파트로 구성)")
    void generatesWellFormedAccessToken() {
        String token = jwtProvider.generateAccessToken("user-1", "test@example.com", Role.USER);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);   // header.payload.signature
    }

    @Test
    @DisplayName("generateRefreshToken()도 파싱 가능한 형태의 JWT 문자열을 생성한다")
    void generatesWellFormedRefreshToken() {
        String token = jwtProvider.generateRefreshToken("user-1");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("같은 사용자라도 시간이 지나면 다른 토큰이 생성된다 (issuedAt이 달라짐)")
    void tokensDifferWhenIssuedAtDifferentSeconds() throws InterruptedException {
        // JWT의 iat(issuedAt) 클레임은 초 단위로 기록된다. 10ms처럼 짧은 간격으로는
        // 같은 초 안에 흡수되어 iat이 완전히 동일하게 직렬화되고, 그러면 서명까지
        // 같은 입력값으로 계산되어 토큰 전체가 우연히 똑같이 나온다 — 실제로 겪었다.
        // 최소 1초 이상 차이를 둬야 iat이 달라지는 걸 확실히 재현할 수 있다.
        String token1 = jwtProvider.generateAccessToken("user-1", "test@example.com", Role.USER);
        Thread.sleep(1100);
        String token2 = jwtProvider.generateAccessToken("user-1", "test@example.com", Role.USER);

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("getClaims()는 JwtValidator에 위임한다 (발급과 검증의 책임 분리)")
    void delegatesClaimsRetrievalToJwtValidator() {
        given(jwtValidator.validate("some-token")).willReturn(claims);

        Claims result = jwtProvider.getClaims("some-token");

        assertThat(result).isSameAs(claims);
    }

    @Test
    @DisplayName("getRemaining()은 만료 시각에서 현재 시각을 뺀 값을 반환한다")
    void calculatesRemainingTimeCorrectly() {
        Date futureExpiration = new Date(System.currentTimeMillis() + 60000);
        given(jwtValidator.validate("token")).willReturn(claims);
        given(claims.getExpiration()).willReturn(futureExpiration);

        long remaining = jwtProvider.getRemaining("token");

        // 정확히 60000이 아니라, 테스트 실행 사이의 시간 오차를 감안해 범위로 확인
        assertThat(remaining).isBetween(58000L, 60000L);
    }

    @Test
    @DisplayName("private key 형식이 잘못되면 INVALID_TOKEN 예외를 던진다")
    void throwsWhenPrivateKeyIsMalformed() {
        JwtProperties badProperties = new JwtProperties();
        badProperties.setPrivateKey("not-a-valid-pem-key");
        badProperties.setAccessTokenExpiration(3600000L);
        JwtProvider badProvider = new JwtProvider(badProperties, jwtValidator);

        assertThatThrownBy(() -> badProvider.generateAccessToken("user-1", "test@example.com", Role.USER))
                .isInstanceOf(BusinessException.class);
    }
}