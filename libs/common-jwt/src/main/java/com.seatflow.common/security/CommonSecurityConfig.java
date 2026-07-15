package com.seatflow.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 이 모듈(common-jwt)만 의존성에 추가하면 서비스는 별도 설정 없이 JWT 검증
 * 빈이 자동으로 뜬다(META-INF/spring/AutoConfiguration.imports로 등록).
 * common-outbox-jpa와 동일한 패턴이다.
 * 컴포넌트 스캔 범위에 기대지 않고 명시적으로 자동 설정을 등록해, 서비스의 스캔 설정과 무관하게 항상 적용되게 한다.
 *
 * 경로별 인가 규칙(authorizeHttpRequests)은 서비스마다 다르므로 여기서 강제하지 않는다.
 * 각 서비스가 자기 SecurityConfig에서 이 필터를 가져다 쓴다.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(JwtValidator.class)
    public JwtValidator jwtValidator(JwtProperties properties) {
        return new JwtValidator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationFilter.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtValidator jwtValidator) {
        return new JwtAuthenticationFilter(jwtValidator);
    }
}