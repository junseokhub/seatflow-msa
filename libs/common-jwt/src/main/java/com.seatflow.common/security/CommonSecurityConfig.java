package com.seatflow.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * 모든 서비스가 공유하는 기본 Security 설정. JWT 필터를 등록하고, 세션을 안 쓰는
 * STATELESS 정책으로 둔다(토큰 기반 인증이라 서버가 세션 상태를 들고 있을 필요가 없다).
 *
 * 경로별 인가 규칙(어떤 API가 인증/특정 권한을 요구하는지)은 서비스마다 다르므로
 * 여기서 강제하지 않는다. 각 서비스가 이 설정을 상속하거나 참고해 자기 SecurityConfig를
 * 둔다. 공통화하는 건 "JWT를 어떻게 검증해 인증 컨텍스트를 채우는가"까지다.
 */
@EnableWebSecurity
public class CommonSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(JwtVerificationProperties.class)
    public JwtVerificationProperties jwtVerificationProperties() {
        return new JwtVerificationProperties();
    }

    @Bean
    @ConditionalOnMissingBean(JwtValidator.class)
    public JwtValidator jwtValidator(JwtVerificationProperties properties) {
        return new JwtValidator(properties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtValidator jwtValidator) {
        return new JwtAuthenticationFilter(jwtValidator);
    }
}