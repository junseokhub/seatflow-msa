package com.seatflow.reservation.controller;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.exception.GlobalExceptionHandler;
import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.reservation.config.SecurityConfig;
import com.seatflow.reservation.exception.ReservationErrorCode;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CancelReservationController.class)
@ContextConfiguration(classes = {CancelReservationController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class CancelReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CancelSagaOrchestrator cancelSagaOrchestrator;
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
    @WithMockUser(username = "user1")
    @DisplayName("인증된 사용자가 취소 요청하면 202를 반환하고 오케스트레이터에 위임한다")
    void authenticatedUserCanRequestCancellation() throws Exception {
        mockMvc.perform(post("/api/reservations/1/cancel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));

        verify(cancelSagaOrchestrator).startCancellation(1L, "user1");
    }

    @Test
    @DisplayName("인증 없이 취소 요청하면 401이 나온다")
    void unauthenticatedCannotRequestCancellation() throws Exception {
        mockMvc.perform(post("/api/reservations/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "other-user")
    @DisplayName("오케스트레이터가 RESERVATION_NOT_OWNED를 던지면 그대로 403으로 전달된다")
    void propagatesNotOwnedErrorFromOrchestrator() throws Exception {
        doAnswer(invocation -> {
            throw new BusinessException(
                    ReservationErrorCode.RESERVATION_NOT_OWNED.getStatus().value(),
                    ReservationErrorCode.RESERVATION_NOT_OWNED.getMessage());
        }).when(cancelSagaOrchestrator).startCancellation(1L, "other-user");

        mockMvc.perform(post("/api/reservations/1/cancel"))
                .andExpect(status().isForbidden());
    }
}