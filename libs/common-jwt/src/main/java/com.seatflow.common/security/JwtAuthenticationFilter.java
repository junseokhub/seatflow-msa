package com.seatflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization 헤더의 JWT를 검증해 Spring Security 인증 컨텍스트를 채운다.
 * 모든 서비스가 이 필터를 공유한다(common-web). 서비스마다 검증 로직을 복붙하지 않는다.
 *
 * 검증 실패(서명 불일치, 만료 등)는 여기서 401을 내지 않고 그냥 인증 컨텍스트를 비워둔 채
 * 다음 필터로 넘긴다. 인증이 필요한 요청인지 여부는 SecurityConfig의 authorizeHttpRequests가
 * 판단하고, 인증 없이 접근하면 Spring Security가 401/403으로 처리한다. 이렇게 해야
 * "인증이 필요 없는 공개 API"(예: 공연 목록 조회)에서 토큰이 없어도 필터가 요청을 막지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtValidator.validate(token);
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                log.debug("Invalid JWT, proceeding unauthenticated: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}