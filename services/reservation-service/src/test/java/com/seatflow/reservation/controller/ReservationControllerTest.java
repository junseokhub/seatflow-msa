package com.seatflow.reservation.controller;

import com.seatflow.common.exception.GlobalExceptionHandler;
import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.reservation.config.SecurityConfig;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import com.seatflow.reservation.service.ReservationService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReservationController는 SecurityConfig가 anyRequest().authenticated()라서
 * 모든 API가 인증을 요구한다(공개 조회가 없음,  예매 정보는 항상 개인정보).
 */
@WebMvcTest(controllers = ReservationController.class)
@ContextConfiguration(classes = {ReservationController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;
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

    private Reservation reservation() {
        return Reservation.builder()
                .userId("user1").showId("show-1").seatId(1L)
                .amount(BigDecimal.valueOf(50000)).showDate(LocalDateTime.now().plusDays(10))
                .status(ReservationStatus.CONFIRMED)
                .build();
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("인증된 사용자가 예매 단건 조회하면 200을 반환한다")
    void authenticatedUserCanGetReservation() throws Exception {
        given(reservationService.getReservation(1L)).willReturn(reservation());

        mockMvc.perform(get("/api/reservations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user1"));
    }

    @Test
    @DisplayName("인증 없이 예매 조회하면 401이 나온다")
    void unauthenticatedCannotGetReservation() throws Exception {
        mockMvc.perform(get("/api/reservations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("본인 userId로 예매 목록 조회하면 200을 반환한다")
    void authenticatedUserCanGetOwnReservations() throws Exception {
        given(reservationService.getUserReservations("user1")).willReturn(List.of(reservation()));

        mockMvc.perform(get("/api/reservations/user/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("타인의 userId로 예매 목록을 조회하려 하면 403이 나온다")
    void cannotGetOtherUsersReservations() throws Exception {
        mockMvc.perform(get("/api/reservations/user/other-user"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 예매 목록 조회하면 401이 나온다")
    void unauthenticatedCannotGetUserReservations() throws Exception {
        mockMvc.perform(get("/api/reservations/user/user1"))
                .andExpect(status().isUnauthorized());
    }
}