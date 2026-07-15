package com.seatflow.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 검증에 필요한 최소 프로퍼티. public key만 갖는다.
 * private key(발급 권한)는 auth-service만 가지며, 여기 포함하지 않는다.
 * 검증만 하는 서비스가 발급 능력까지 갖고 있으면 안 되기 때문이다.
 *
 * auth의 JwtProperties(jwt.private-key, jwt.public-key)와 프로퍼티 접두사(jwt)를 공유한다.
 * 각 서비스 배포 설정에 jwt.public-key 값을 auth와 동일하게 주입해야 한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtVerificationProperties {
    private String publicKey;
}