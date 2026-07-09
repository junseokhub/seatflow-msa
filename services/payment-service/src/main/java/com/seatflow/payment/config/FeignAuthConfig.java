//package com.seatflow.payment.config;
//
//// payment-service에 추가. reservation-service를 Feign으로 호출할 때, 지금 처리 중인
//// 원본 요청의 Authorization 헤더를 그대로 실어 보낸다. 이게 없으면 대상 서비스가
//// 토큰 없는 요청으로 인식해 401/403으로 막는다(14편에서 서비스 간 호출을 안 다뤘던 구멍).
//
//import feign.RequestInterceptor;
//import feign.RequestTemplate;
//import jakarta.servlet.http.HttpServletRequest;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.context.request.RequestContextHolder;
//import org.springframework.web.context.request.ServletRequestAttributes;
//
//@Configuration
//public class FeignAuthConfig {
//
//    @Bean
//    public RequestInterceptor authForwardingInterceptor() {
//        return (RequestTemplate template) -> {
//            ServletRequestAttributes attributes =
//                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
//            if (attributes == null) {
//                return;
//            }
//            HttpServletRequest request = attributes.getRequest();
//            String authorization = request.getHeader("Authorization");
//            if (authorization != null) {
//                template.header("Authorization", authorization);
//            }
//        };
//    }
//}