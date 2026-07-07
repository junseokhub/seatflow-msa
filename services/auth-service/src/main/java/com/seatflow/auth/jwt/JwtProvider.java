package com.seatflow.auth.jwt;

import com.seatflow.auth.config.properties.JwtProperties;
import com.seatflow.auth.exception.AuthErrorCode;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.security.JwtValidator;
import com.seatflow.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * 발급 전용. private key로 서명하는 것만 담당한다.
 * 검증(getClaims)은 common-jwt의 JwtValidator에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private final JwtValidator jwtValidator;

    public String generateAccessToken(String userId, String email, Role role) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role.name())
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiration()))
                .signWith(getPrivateKey())
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiration()))
                .signWith(getPrivateKey())
                .compact();
    }

    public Claims getClaims(String token) {
        return jwtValidator.validate(token);
    }

    public long getRemaining(String token) {
        return getClaims(token).getExpiration().getTime() - System.currentTimeMillis();
    }

    private PrivateKey getPrivateKey() {
        try {
            String pem = jwtProperties.getPrivateKey()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_TOKEN.getStatus().value(),
                    AuthErrorCode.INVALID_TOKEN.getMessage()
            );
        }
    }

    public long getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }
}