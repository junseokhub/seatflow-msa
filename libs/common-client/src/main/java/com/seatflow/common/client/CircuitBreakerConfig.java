package com.seatflow.common.client;

// Feign + Resilience4j 서킷 브레이커 기본 설정.
//
// 왜 필요한가: 서비스 간 동기 호출(payment -> reservation, payment -> coupon)이
// 늘어나면서, 호출 대상이 느려지거나 죽었을 때 그 대기가 호출한 쪽까지 전파되는
// 위험이 생겼다. 서킷 브레이커는 "계속 실패하는 대상에는 아예 요청을 안 보내고
// 즉시 실패 처리"해서, 한 서비스의 장애가 연쇄적으로 번지는 걸 막는다.
//
// Spring Cloud OpenFeign은 spring.cloud.openfeign.circuitbreaker.enabled=true 설정만
// 켜면, 등록된 모든 FeignClient 호출을 CircuitBreakerFactory로 자동으로 감싼다.
// 여기서는 그 CircuitBreakerFactory가 쓸 기본 정책(실패율, 타임아웃 등)을 정의한다.

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfig {

    /**
     * 모든 Feign 호출에 적용되는 기본 정책.
     * - 최근 10건 중 실패율 50% 이상이면 서킷 오픈(이후 요청 즉시 실패, 대상을 안 부름)
     * - 오픈 후 5초 지나면 반열림으로 전환, 3건만 시험적으로 흘려보내 회복 여부 확인
     * - 개별 호출은 3초 넘으면 타임아웃으로 실패 처리 — 이게 핵심이다. 대상 서비스가
     *   느려져도 호출한 쪽 스레드가 무한정 잡혀있지 않게 한다.
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowType(SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(3))
                        .build())
                .build());
    }
}