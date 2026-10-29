package com.seatflow.auth.service;

import com.seatflow.auth.config.properties.ServiceClientProperties;
import com.seatflow.auth.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * OAuth2 Client Credentials 그랜트로 서비스 토큰을 발급한다.
 * 클라이언트 인증은 client_secret_basic(Authorization: Basic base64(urlencode(id):urlencode(secret)))이다.
 */
@Service
@RequiredArgsConstructor
public class ServiceTokenService {

    private final ServiceClientProperties serviceClientProperties;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * 클라이언트 인증에 실패하면 이유(헤더 형식, 없는 client_id, 틀린 시크릿)를 구분하지 않고 empty를 반환한다.
     * 어떤 client_id가 등록돼 있는지 응답으로 드러내지 않기 위해서다.
     */
    public Optional<ServiceToken> issue(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return Optional.empty();
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String clientId = URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8);
        String clientSecret = URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8);

        String secretHash = serviceClientProperties.getServiceClients().get(clientId);
        if (secretHash == null || !passwordEncoder.matches(clientSecret, secretHash)) {
            return Optional.empty();
        }
        return Optional.of(new ServiceToken(
                jwtProvider.generateServiceToken(clientId),
                jwtProvider.getServiceTokenExpiration() / 1000));
    }

    public record ServiceToken(String accessToken, long expiresInSeconds) {}
}
