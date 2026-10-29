package com.seatflow.auth.controller;

import com.seatflow.auth.service.ServiceTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OAuth2 토큰 엔드포인트(RFC 6749 4.4 Client Credentials). 서비스 간 호출에 쓸 서비스 토큰을 발급한다.
 * 사용자 요청이 없는 곳(Kafka 컨슈머, 스케줄러)에서도 서비스가 자기 신원으로 다른 서비스를 호출할 수 있게 한다.
 *
 * 응답은 공통 ApiResponse로 감싸지 않고 RFC 형식(access_token/token_type/expires_in, 오류는 error)을 그대로 따른다.
 * 호출하는 쪽이 표준 OAuth2 클라이언트(Spring Security OAuth2 Client)라 이 형식을 기대한다.
 */
@RestController
@RequiredArgsConstructor
public class OAuth2TokenController {

    private final ServiceTokenService serviceTokenService;

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (grantType == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        if (!"client_credentials".equals(grantType)) {
            return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
        }
        return serviceTokenService.issue(authorization)
                .map(token -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())   // RFC 6749 5.1: 토큰 응답은 캐시 금지
                        .body(Map.<String, Object>of(
                                "access_token", token.accessToken(),
                                "token_type", "Bearer",
                                "expires_in", token.expiresInSeconds())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"seatflow\"")
                        .body(Map.of("error", "invalid_client")));
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String error) {
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
