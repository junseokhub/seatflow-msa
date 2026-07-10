package com.seatflow.coupon.controller;

import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.coupon.config.SecurityConfig;
import com.seatflow.coupon.domain.Coupon;
import com.seatflow.coupon.domain.CouponStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CouponController.class)
@ContextConfiguration(classes = {CouponController.class, SecurityConfig.class})
class CouponControllerTest {

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
    @WithMockUser(username = "user1")
    @DisplayName("인증된 사용자가 발급 요청하면 200과 발급된 쿠폰을 반환한다")
    void issueCouponReturnsCoupon() throws Exception {
        Coupon coupon = Coupon.builder()
                .campaignId(1L)
                .userId("user1")
                .discountAmount(BigDecimal.valueOf(5000))
                .build();
        given(couponService.issueCoupon(1L, "user1")).willReturn(coupon);

        mockMvc.perform(post("/api/coupons/campaigns/1/issue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(CouponStatus.ISSUED.name()));
    }

    @Test
    @DisplayName("인증 없이 발급을 시도하면 401이 나온다")
    void unauthenticatedCannotIssue() throws Exception {
        mockMvc.perform(post("/api/coupons/campaigns/1/issue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("내 쿠폰 목록 조회는 인증된 사용자만 가능하다")
    void getMyCouponsRequiresAuthentication() throws Exception {
        given(couponService.getUserCoupons("user1")).willReturn(java.util.List.of());

        mockMvc.perform(get("/api/coupons/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}