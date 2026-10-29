package com.seatflow.auth.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 서비스 간 호출용 OAuth2 클라이언트 등록 정보. client_id -> BCrypt 해시된 client_secret.
 * 사용자 비밀번호와 같은 이유로 auth는 평문 시크릿을 보관하지 않는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth")
public class ServiceClientProperties {
    private Map<String, String> serviceClients = new HashMap<>();
}
