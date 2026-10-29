package com.seatflow.coupon.config;

import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.common.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * coupon-service 인가 규칙. 캠페인 생성은 @PreAuthorize로 관리자 제한(컨트롤러에 있음).
 * 나머지(발급, 조회)는 인증만 필요. /internal/**은 서비스 간 호출 전용이라 이 서비스
 * 앞단(Kong 등)에서 외부 노출을 막아야 하지만, 지금 범위에서는 인증 규칙만 둔다.
 *
 * 변경: /internal/**은 서비스 토큰(ROLE_SERVICE, OAuth2 Client Credentials로 발급)만 허용한다.
 * 인증만 요구하던 때는 로그인한 사용자가 자기 토큰으로 confirm/restore를 직접 호출할 수 있었고,
 * 사용자 요청이 없는 호출(Kafka 컨슈머, 스케줄러)은 전파할 토큰이 없어 항상 401이 났다.
 * Gateway 노출 차단은 여전히 별도로 필요하다(심층 방어).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/**").hasRole(Role.SERVICE.name())
                        .requestMatchers(HttpMethod.GET, "/api/coupons/campaigns", "/api/coupons/campaigns/*").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}