package com.seatflow.show.controller;

import com.seatflow.common.event.show.SeatGradeType;
import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.show.config.SecurityConfig;
import com.seatflow.show.domain.SeatGrade;
import com.seatflow.show.domain.Show;
import com.seatflow.show.service.ShowService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NOTE: GlobalExceptionHandler를 @ContextConfiguration에 추가해야 BusinessException이
 * 의도한 상태코드로 변환된다
 * 반복 확인한 패턴.
 */
@WebMvcTest(controllers = ShowController.class)
@ContextConfiguration(classes = {
        ShowController.class,
        SecurityConfig.class,
        com.seatflow.common.exception.GlobalExceptionHandler.class
})
class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShowService showService;
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

    private Show show() {
        return Show.builder()
                .id("show-1").title("제목").venue("공연장")
                .showDate(LocalDateTime.of(2026, 12, 25, 19, 0))
                .seatGrades(List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("공연 조회는 인증 없이도 가능하다")
    void anyoneCanGetShow() throws Exception {
        given(showService.getShow("show-1")).willReturn(show());

        mockMvc.perform(get("/api/shows/show-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("공연 목록 조회도 인증 없이 가능하다")
    void anyoneCanGetShowList() throws Exception {
        given(showService.getShows()).willReturn(List.of(show()));

        mockMvc.perform(get("/api/shows"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 공연 생성하면 200을 반환한다")
    void adminCanCreateShow() throws Exception {
        given(showService.createShow(any())).willReturn(show());

        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","venue":"공연장","showDate":"2026-12-25T19:00:00",
                                 "seatGrades":[{"grade":"VIP","capacity":10,"price":100000}]}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 USER 권한으로 공연 생성 시도하면 403이 나온다")
    void nonAdminCannotCreateShow() throws Exception {
        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","venue":"공연장","showDate":"2026-12-25T19:00:00",
                                 "seatGrades":[{"grade":"VIP","capacity":10,"price":100000}]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 공연 생성하면 401이 나온다")
    void unauthenticatedCannotCreateShow() throws Exception {
        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","venue":"공연장","showDate":"2026-12-25T19:00:00",
                                 "seatGrades":[{"grade":"VIP","capacity":10,"price":100000}]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("존재하지 않는 등급(enum에 없는 값)으로 공연 생성 요청하면 400이 나온다 (500 아님)")
    void invalidSeatGradeEnumReturnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","venue":"공연장","showDate":"2026-12-25T19:00:00",
                                 "seatGrades":[{"grade":"NOT_A_REAL_GRADE","capacity":10,"price":100000}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 공연을 수정하면 200을 반환한다")
    void adminCanUpdateShow() throws Exception {
        given(showService.updateShow("show-1", "새제목", null, null)).willReturn(show());

        mockMvc.perform(patch("/api/shows/show-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새제목"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 USER 권한으로 공연 수정 시도하면 403이 나온다")
    void nonAdminCannotUpdateShow() throws Exception {
        mockMvc.perform(patch("/api/shows/show-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새제목"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 공연을 삭제하면 200을 반환한다")
    void adminCanDeleteShow() throws Exception {
        mockMvc.perform(delete("/api/shows/show-1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 USER 권한으로 공연 삭제 시도하면 403이 나온다")
    void nonAdminCannotDeleteShow() throws Exception {
        mockMvc.perform(delete("/api/shows/show-1"))
                .andExpect(status().isForbidden());
    }
}