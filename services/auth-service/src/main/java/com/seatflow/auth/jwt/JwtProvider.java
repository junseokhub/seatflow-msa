package com.seatflow.auth.jwt;

import com.seatflow.auth.config.properties.JwtProperties;
import com.seatflow.auth.exception.AuthErrorCode;
import com.seatflow.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;

    public String generateAccessToken(String userId, String email) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
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
        return Jwts.parser()
                .verifyWith(getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
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

    private PublicKey getPublicKey() {
        try {
            String pem = jwtProperties.getPublicKey()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
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