package com.seatflow.common.client;

/**
 * 서비스 간 Feign 호출에 원본 요청의 Authorization 헤더를 그대로 전파한다.
 * 14편에서 각 서비스가 JWT를 직접 검증하는 구조(패턴 2)로 갔는데,
 * 서비스 간 동기 호출도 결국 누군가의 요청 처리 중 파생된 호출이라 그 요청의 인증 정보가 전달돼야 대상 서비스의 검증을 통과한다.
 * 이게 없으면 요청이 처리 중이던 사용자가 이미 인증됐어도, 서비스 간 호출 자체는 토큰 없는 요청으로 취급되어 401/403이 난다.
 * 이 빈은 인터셉터일 뿐 특정 FeignClient에 의존하지 않으므로,
 * common-clients를 의존하는 어떤 서비스에도 안전하게 등록할 수 있다(URL 프로퍼티가 없어도 이 빈 자체는 실패하지 않는다.
 * @FeignClient 인터페이스만 URL을 필요로 한다).
 */

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@AutoConfiguration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;   // 요청 컨텍스트 밖(배치, 스케줄러 등)에서 호출된 경우
            }
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader("Authorization");
            if (authorization != null) {
                template.header("Authorization", authorization);
            }
        };
    }
}