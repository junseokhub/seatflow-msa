package com.seatflow.auth.controller;

import com.seatflow.auth.config.SecurityConfig;
import com.seatflow.auth.service.AuthService;
import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.common.security.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NOTE: GlobalExceptionHandler를 @ContextConfiguration에 추가해야 BusinessException이
 * 의도한 상태코드(401 등)로 변환된다.
 */
@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = {
        AuthController.class,
        SecurityConfig.class,
        com.seatflow.common.exception.GlobalExceptionHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void stubJwtFilterToPassThrough() throws Exception {
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("유효한 요청으로 회원가입하면 200을 반환한다 (인증 불필요)")
    void signupReturnsOk() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123","name":"테스트"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 400이 나온다")
    void signupWithInvalidEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"password123","name":"테스트"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("필수 필드가 빠지면 400이 나온다")
    void signupWithMissingFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("올바른 로그인 요청은 200과 accessToken을 반환하고 refresh_token 쿠키를 설정한다")
    void loginReturnsTokenAndSetsCookie() throws Exception {
        given(authService.login(anyString(), anyString()))
                .willReturn(new AuthService.TokenResult("access-token-value", "refresh-token-value"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token-value"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    @DisplayName("refresh_token 쿠키 없이 refresh 요청하면 400이 나온다")
    void refreshWithoutCookieReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Authorization 헤더 없이 validate 요청하면 400이 나온다")
    void validateWithoutAuthorizationHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/auth/validate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유효한 토큰으로 validate 요청하면 200과 사용자 정보를 반환한다")
    void validateReturnsUserInfo() throws Exception {
        given(authService.validate("valid-token"))
                .willReturn(new AuthService.ValidateResult("user-1", "test@example.com", Role.USER));

        mockMvc.perform(get("/api/auth/validate")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("유효한 refresh_token 쿠키로 재발급 요청하면 200과 새 accessToken, 새 쿠키를 반환한다")
    void refreshReturnsNewTokenAndCookie() throws Exception {
        given(authService.refresh("valid-refresh-cookie"))
                .willReturn(new AuthService.TokenResult("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "valid-refresh-cookie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().value("refresh_token", "new-refresh-token"));
    }

    @Test
    @WithMockUser(username = "user-1")
    @DisplayName("Authorization 헤더와 refresh_token 쿠키가 모두 있으면 로그아웃이 성공하고 쿠키가 삭제된다")
    void logoutSucceedsAndDeletesCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer some-access-token")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge("refresh_token", 0));   // 삭제 지시(maxAge=0) 확인

        verify(authService).logout("some-access-token", "some-refresh-token");
    }

    @Test
    @WithMockUser(username = "user-1")
    @DisplayName("Authorization 헤더 없이 로그아웃 요청하면 400이 나온다")
    void logoutWithoutAuthorizationHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user-1")
    @DisplayName("refresh_token 쿠키 없이 로그아웃 요청하면 400이 나온다")
    void logoutWithoutCookieReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer some-access-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 로그아웃 요청하면 401이 나온다 (logout은 permitAll 대상이 아님)")
    void unauthenticatedCannotLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer some-access-token")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isUnauthorized());
    }
}