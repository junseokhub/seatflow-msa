package com.seatflow.coupon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.coupon.config.SecurityConfig;
import com.seatflow.coupon.domain.CouponCampaign;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 계층만 검증한다 — 실제 서비스 로직(CouponService)은 Mock으로 대체하고,
 * "요청이 올바른 서비스 메서드로 전달되는지", "인가 규칙(@PreAuthorize)이 실제로
 * 걸리는지", "응답이 올바른 상태코드/형식으로 나가는지"만 확인한다.
 *
 * 주의: @WebMvcTest는 기본적으로 컨트롤러 관련 빈만 로드한다. coupon-service의
 * SecurityConfig(@Configuration)가 이 테스트 슬라이스에 자동으로 포함되지 않으면
 * @PreAuthorize 검증(403/401 관련 테스트)이 기대와 다르게 동작할 수 있다 — 이 경우
 * @Import(SecurityConfig.class)를 클래스 레벨에 명시적으로 추가해야 한다. 실행해보고
 * 403/401 테스트가 실패하면 이 부분부터 확인할 것.
 */
@WebMvcTest(CouponCampaignController.class)
@ContextConfiguration(classes = {CouponCampaignController.class, SecurityConfig.class})
class CouponCampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    @Qualifier("redisCouponService")
    private CouponService couponService;


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
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자가 캠페인 생성 요청을 보내면 200과 생성된 캠페인을 반환한다")
    void adminCanCreateCampaign() throws Exception {
        CouponCampaign campaign = CouponCampaign.builder()
                .name("오픈 기념 쿠폰")
                .discountAmount(BigDecimal.valueOf(5000))
                .totalQuantity(100)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        given(couponService.createCampaign(any(), any(), anyInt(), any())).willReturn(campaign);

        mockMvc.perform(post("/api/coupons/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"오픈 기념 쿠폰","discountAmount":5000,"totalQuantity":100,"expiresAt":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("오픈 기념 쿠폰"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자가 캠페인 생성을 시도하면 403이 나온다")
    void nonAdminCannotCreateCampaign() throws Exception {
        mockMvc.perform(post("/api/coupons/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"test","discountAmount":1000,"totalQuantity":1,"expiresAt":null}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 캠페인 생성을 시도하면 401이 나온다")
    void unauthenticatedCannotCreateCampaign() throws Exception {
        mockMvc.perform(post("/api/coupons/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"test","discountAmount":1000,"totalQuantity":1,"expiresAt":null}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("캠페인 목록 조회는 인증 없이도 가능하다")
    void anyoneCanListCampaigns() throws Exception {
        given(couponService.getCampaigns()).willReturn(java.util.List.of());

        mockMvc.perform(get("/api/coupons/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("필수 필드(name) 없이 캠페인 생성을 요청하면 400이 나온다")
    @WithMockUser(roles = "ADMIN")
    void createCampaignFailsValidationWithoutName() throws Exception {
        mockMvc.perform(post("/api/coupons/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountAmount":1000,"totalQuantity":1,"expiresAt":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("캠페인 단건 조회는 인증 없이도 가능하다")
    void anyoneCanGetCampaignById() throws Exception {
        CouponCampaign campaign = CouponCampaign.builder()
                .name("단건 조회 테스트")
                .discountAmount(BigDecimal.valueOf(2000))
                .totalQuantity(50)
                .expiresAt(null)
                .build();
        given(couponService.getCampaign(1L)).willReturn(campaign);

        mockMvc.perform(get("/api/coupons/campaigns/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("단건 조회 테스트"));
    }
}