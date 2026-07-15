package com.seatflow.common.exception;

import com.seatflow.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.error("Business exception occurred: ", e);
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        log.error("Validation exception occurred: ", e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("유효성 검증 실패"));
    }

    /**
     * @RequestHeader(required=true, 기본값)인 헤더가 요청에 없을 때 Spring이 던지는 예외.
     * 이게 별도 핸들러 없이 Exception.class로 떨어지면 클라이언트 잘못(400)이 서버 오류(500)로 응답돼, 클라이언트가 원인을 파악하기 어려워진다.
     * payment-service의 Idempotency-Key 헤더 누락 테스트로 실제로 겪었다.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException e) {
        log.warn("Missing required header: {}", e.getHeaderName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("필수 헤더가 없습니다: " + e.getHeaderName()));
    }

    /**
     * @PathVariable/@RequestParam의 타입이 안 맞을 때(예: Long 파라미터에 "abc" 전달)
     * Spring이 던지는 예외. 같은 이유로 400이어야 한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Type mismatch for parameter: {}", e.getName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("잘못된 요청 파라미터입니다: " + e.getName()));
    }

    /**
     * @CookieValue(required=true, 기본값)인 쿠키가 요청에 없을 때 Spring이 던지는 예외.
     * MissingRequestHeaderException과 같은 이유(클라이언트 잘못이 500으로 응답되는 문제)로 별도 핸들러가 필요하다.
     * auth-service의 refresh_token 쿠키 누락 테스트로 실제로 겪었다.
     */
    @ExceptionHandler(org.springframework.web.bind.MissingRequestCookieException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestCookie(
            org.springframework.web.bind.MissingRequestCookieException e) {
        log.warn("Missing required cookie: {}", e.getCookieName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("필수 쿠키가 없습니다: " + e.getCookieName()));
    }

    /**
     * 요청 바디를 JSON으로 파싱하다가 실패했을 때(예: enum에 없는 문자열 값, 잘못된 JSON 문법) Spring이 던지는 예외.
     * MissingRequestHeaderException, MissingRequestCookieException과 같은 이유로 별도 핸들러가 필요하다.
     * show-service의 잘못된 enum 값 테스트로 발견했다.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(AuthorizationDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("접근 권한이 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Exception occurred: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("서버 오류가 발생했습니다."));
    }
}