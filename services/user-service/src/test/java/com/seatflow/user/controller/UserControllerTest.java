package com.seatflow.user.controller;

import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.user.config.SecurityConfig;
import com.seatflow.user.domain.User;
import com.seatflow.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler를 @ContextConfiguration에 추가해야 BusinessException이 의도한 상태코드(403 등)로 변환된다.
 * reservation/payment/auth에서 반복 확인한 패턴.
 */
@WebMvcTest(controllers = UserController.class)
@ContextConfiguration(classes = {
        UserController.class,
        SecurityConfig.class,
        com.seatflow.common.exception.GlobalExceptionHandler.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
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

    private User user(String id) {
        return User.builder().id(id).email("test@example.com").name("테스트").build();
    }

    @Test
    @WithMockUser(username = "user-1")
    @DisplayName("본인 프로필을 조회하면 200을 반환한다")
    void ownerCanGetOwnProfile() throws Exception {
        given(userService.getUser("user-1")).willReturn(user("user-1"));

        mockMvc.perform(get("/api/users/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "other-user")
    @DisplayName("타인의 프로필을 조회하면 403이 나온다")
    void nonOwnerCannotGetOtherProfile() throws Exception {
        mockMvc.perform(get("/api/users/user-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 프로필을 조회하면 401이 나온다")
    void unauthenticatedCannotGetProfile() throws Exception {
        mockMvc.perform(get("/api/users/user-1"))
                .andExpect(status().isUnauthorized());
    }
}