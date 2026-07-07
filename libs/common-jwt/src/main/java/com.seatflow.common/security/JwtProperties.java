package com.seatflow.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 검증에 필요한 공통 프로퍼티. common-web에 두어 모든 서비스가 공유한다.
 * publicKey만 갖는다 — private key(발급 권한)는 auth-service만의 확장 프로퍼티
 * (auth.jwt.private-key 등 별도 prefix)로 분리해, 검증만 하는 서비스가 발급
 * 능력까지 갖지 않게 한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String publicKey;
}