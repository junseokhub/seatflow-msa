package com.seatflow.payment.controller;

import com.seatflow.common.exception.GlobalExceptionHandler;
import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.payment.config.SecurityConfig;
import com.seatflow.payment.domain.Payment;
import com.seatflow.payment.domain.PaymentMethod;
import com.seatflow.payment.domain.PaymentStatus;
import com.seatflow.payment.service.PaymentFacade;
import com.seatflow.payment.service.PaymentService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NOTE: GlobalExceptionHandler(정확한 클래스명은 common-web 등에서 확인 필요)를
 * @ContextConfiguration에 추가해야 BusinessException이 의도한 상태코드로 변환된다.
 * reservation-service에서 겪었던 것과 같은 이유.
 */
@WebMvcTest(controllers = PaymentController.class)
@ContextConfiguration(classes = {PaymentController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentFacade paymentFacade;
    @MockitoBean
    private PaymentService paymentService;
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

    private Payment payment(String userId) {
        return Payment.builder()
                .reservationId(1L).userId(userId)
                .amount(BigDecimal.valueOf(50000)).paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("Idempotency-Key와 함께 결제 요청하면 200과 결과를 반환하고 파사드에 위임한다")
    void processesPaymentWithIdempotencyKey() throws Exception {
        given(paymentFacade.pay(anyString(), any())).willReturn(payment("user1"));

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":1,"amount":50000,"paymentMethod":"CREDIT_CARD","couponId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(paymentFacade).pay(org.mockito.ArgumentMatchers.eq("key-123"), any());
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("Idempotency-Key 헤더가 없으면 400이 나온다")
    void missingIdempotencyKeyHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":1,"amount":50000,"paymentMethod":"CREDIT_CARD","couponId":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 결제 요청하면 401이 나온다")
    void unauthenticatedCannotProcessPayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":1,"amount":50000,"paymentMethod":"CREDIT_CARD","couponId":null}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("본인 결제를 조회하면 200을 반환한다")
    void ownerCanGetPayment() throws Exception {
        given(paymentService.getPayment(1L)).willReturn(payment("user1"));

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "other-user")
    @DisplayName("타인의 결제를 조회하면 403이 나온다")
    void nonOwnerCannotGetPayment() throws Exception {
        given(paymentService.getPayment(1L)).willReturn(payment("user1"));   // user1 소유

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 결제 조회하면 401이 나온다")
    void unauthenticatedCannotGetPayment() throws Exception {
        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isUnauthorized());
    }
}