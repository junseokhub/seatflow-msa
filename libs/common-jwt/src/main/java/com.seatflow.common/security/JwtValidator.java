package com.seatflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT 검증 전용 컴포넌트. auth의 JwtProvider와 검증 로직(getPublicKey, getClaims)은
 * 동일하지만, 발급(private key, generateAccessToken 등)은 포함하지 않는다.
 * 서비스가 검증만 할 수 있고 발급은 할 수 없게, 능력을 명확히 분리하기 위함이다.
 */
@Component
@RequiredArgsConstructor
public class JwtValidator {

    private final JwtVerificationProperties properties;

    /**
     * 서명과 만료를 검증하고 클레임을 반환한다.
     * 서명이 안 맞거나 만료됐으면 JwtException이 던져진다 — 호출자가 401 등으로 변환한다.
     */
    public Claims validate(String token) {
        return Jwts.parser()
                .verifyWith(getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private PublicKey getPublicKey() {
        try {
            String pem = properties.getPublicKey()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new JwtException("Invalid public key configuration", e);
        }
    }
}