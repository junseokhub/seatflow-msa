package com.seatflow.auth.controller;

import com.seatflow.auth.config.SecurityConfig;
import com.seatflow.auth.config.properties.ServiceClientProperties;
import com.seatflow.auth.jwt.JwtProvider;
import com.seatflow.auth.service.ServiceTokenService;
import com.seatflow.common.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 토큰 엔드포인트는 인증 없이 호출되지만(permitAll), client_id/secret이 맞아야만 토큰이 나온다.
 * ServiceTokenService는 실제 BCrypt 비교까지 타도록 진짜 빈을 쓰고, 서명(JwtProvider)만 목으로 둔다.
 */
@WebMvcTest(controllers = OAuth2TokenController.class)
@ContextConfiguration(classes = {
        OAuth2TokenController.class,
        ServiceTokenService.class,
        SecurityConfig.class,
        OAuth2TokenControllerTest.ClientRegistry.class
})
class OAuth2TokenControllerTest {

    private static final String SECRET = "test-secret";

    @TestConfiguration
    static class ClientRegistry {
        @Bean
        ServiceClientProperties serviceClientProperties() {
            ServiceClientProperties properties = new ServiceClientProperties();
            // application.yml의 auth.service-clients가 이 빈에 바인딩되므로 겹치지 않는 client_id를 쓴다
            properties.setServiceClients(new HashMap<>(Map.of("test-service", new BCryptPasswordEncoder().encode(SECRET))));
            return properties;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtProvider jwtProvider;
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
    @DisplayName("등록된 client_id/secret으로 client_credentials 요청하면 RFC 형식의 서비스 토큰을 발급한다")
    void issuesServiceTokenForValidClient() throws Exception {
        given(jwtProvider.generateServiceToken("test-service")).willReturn("service-token");
        given(jwtProvider.getServiceTokenExpiration()).willReturn(300_000L);

        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("test-service", SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.access_token").value("service-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300));
    }

    @Test
    @DisplayName("시크릿이 틀리면 401 invalid_client를 반환하고 토큰을 만들지 않는다")
    void rejectsWrongSecret() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("test-service", "wrong"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    @DisplayName("등록되지 않은 client_id도 같은 401 invalid_client로 응답해 존재 여부를 숨긴다")
    void rejectsUnknownClient() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("unknown-service", SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    @DisplayName("client_credentials가 아닌 grant_type은 400 unsupported_grant_type이다")
    void rejectsUnsupportedGrantType() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("test-service", SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor httpBasic(String id, String secret) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic(id, secret);
    }
}
