package com.seatflow.auth.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * auth 전용 발급 프로퍼티. private key(서명 권한)와 만료 시간만 갖는다.
 * public key는 common-jwt의 JwtProperties(prefix="jwt")가 공통으로 갖고 있으므로
 * 여기서는 중복하지 않는다. prefix를 "auth.jwt"로 분리해, 이 값을 채우는 배포
 * 설정이 auth-service에만 존재하도록 한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {
    private String privateKey;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;
}