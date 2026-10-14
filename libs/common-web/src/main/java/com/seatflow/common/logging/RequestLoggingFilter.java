package com.seatflow.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 모든 HTTP 요청을 "POST /api/payments 201 35ms" 형태로 한 줄 남긴다.
 * 쿼리스트링은 토큰 등 민감정보가 섞일 수 있어 남기지 않는다.
 * 응답 헤더 X-Trace-Id로 traceId를 돌려줘서, 클라이언트가 받은 오류를 그 ID로 바로 로그에서 찾을 수 있게 한다.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            response.setHeader("X-Trace-Id", traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), System.currentTimeMillis() - start);
        }
    }

    // 트레이싱 필터(HIGHEST_PRECEDENCE + 1) 안쪽이라 MDC에 traceId가 있고,
    // Security 필터보다 바깥이라 401/403도 찍힌다.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
