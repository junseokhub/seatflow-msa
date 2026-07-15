package com.seatflow.coupon.controller;

import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.coupon.config.SecurityConfig;
import com.seatflow.coupon.service.CouponService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment-service가 Feign으로 호출하는 서비스 간 API.
 * 지금 SecurityConfig 기준으로 /internal/** 경로도 다른 API와 동일하게 인증을 요구한다(별도 permitAll 처리가 없다)
 * payment가 이 호출을 할 때 FeignAuthConfig(common-clients)로 원본 요청의 Authorization 헤더를 전파하기 때문에 실제로는 인증된 채로 온다.
 * 여기서는 그 전제를 깔고, 인증된 상태에서 각 엔드포인트가 서비스 메서드를 올바른 파라미터로 호출하고 응답을 올바르게 조립하는지만 확인한다.
 * 이 API를 외부에서 직접(Kong 등을 거쳐) 호출할 수 있는지는 Kong/Ingress 라우팅 설정의 몫이라 이 테스트 범위 밖이다.
 * 원래는 내부 전용으로 노출을 막아야 하는 지점이라는 것만 참고로 남겨둔다.
 */
@WebMvcTest(controllers = CouponInternalController.class)
@ContextConfiguration(classes = {CouponInternalController.class, SecurityConfig.class})
class CouponInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    @Qualifier("redisCouponService")
    private CouponService couponService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void stubJwtFilterToPassThrough() throws Exception {
        // 목 필터가 실제로 doFilter()를 호출하지 않으면 체인이 끊겨 컨트롤러에 도달하지 못한다.
        // 그대로 다음 필터로 흘려보내도록 스텁 처리한다.
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("validate는 couponId, userId를 그대로 서비스에 전달하고 할인액을 응답한다")
    void validateDelegatesToService() throws Exception {
        given(couponService.validateForReservation(1L, "user1"))
                .willReturn(BigDecimal.valueOf(5000));

        mockMvc.perform(post("/internal/coupons/1/validate")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(5000));
    }

    @Test
    @WithMockUser
    @DisplayName("confirm은 couponId, userId, reservationId를 그대로 서비스에 전달한다")
    void confirmDelegatesToServiceWithAllParams() throws Exception {
        mockMvc.perform(post("/internal/coupons/1/confirm")
                        .param("userId", "user1")
                        .param("reservationId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(couponService).confirmForReservation(1L, "user1", 100L);
    }

    @Test
    @WithMockUser
    @DisplayName("restore는 reservationId만으로 복원을 호출한다")
    void restoreDelegatesToServiceWithReservationId() throws Exception {
        mockMvc.perform(post("/internal/coupons/reservations/100/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(couponService).restoreByReservation(100L);
    }

    @Test
    @DisplayName("인증 없이 validate를 호출하면 401이 나온다 (내부 API도 인증 필터를 거친다)")
    void unauthenticatedValidateFails() throws Exception {
        mockMvc.perform(post("/internal/coupons/1/validate")
                        .param("userId", "user1"))
                .andExpect(status().isUnauthorized());
    }
}