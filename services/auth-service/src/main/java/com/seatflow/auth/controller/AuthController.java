package com.seatflow.auth.controller;

import com.seatflow.auth.dto.LoginRequest;
import com.seatflow.auth.dto.SignupRequest;
import com.seatflow.auth.dto.TokenResponse;
import com.seatflow.auth.dto.ValidateResponse;
import com.seatflow.auth.service.AuthService;
import com.seatflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@RequestBody @Valid SignupRequest request) {
        authService.signup(request.email(), request.password(), request.name());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response) {
        AuthService.TokenResult result = authService.login(request.email(), request.password());

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", result.refreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/api/auth/refresh")
                .maxAge(Duration.ofDays(7))
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(result.accessToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue("refresh_token") String refreshToken,
            HttpServletResponse response) {
        AuthService.TokenResult result = authService.refresh(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", result.refreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/api/auth/refresh")
                .maxAge(Duration.ofDays(7))
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.ok(new TokenResponse(result.accessToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorization,
            @CookieValue("refresh_token") String refreshToken,
            HttpServletResponse response) {
        String accessToken = authorization.replace("Bearer ", "");
        authService.logout(accessToken, refreshToken);

        ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/api/auth/refresh")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<ValidateResponse>> validate(
            @RequestHeader("Authorization") String authorization) {
        String accessToken = authorization.replace("Bearer ", "");
        AuthService.ValidateResult result = authService.validate(accessToken);
        return ResponseEntity.ok(ApiResponse.ok(new ValidateResponse(result.userId(), result.email(), result.role())));
    }

}