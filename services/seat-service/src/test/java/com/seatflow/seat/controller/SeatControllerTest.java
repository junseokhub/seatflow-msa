package com.seatflow.seat.controller;

import com.seatflow.common.security.JwtAuthenticationFilter;
import com.seatflow.seat.config.SecurityConfig;
import com.seatflow.seat.service.SeatService;
import com.seatflow.seat.sse.SeatEmitterStore;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * 컨트롤러 계층만 검증한다. coupon-service에서 겪었던 함정 두 가지를 반영했다:
 * (1) @WebMvcTest가 메인 클래스를 자동 채택해 entityManagerFactory 등을 못 찾는
 *     문제 -> @ContextConfiguration으로 정확히 이 컨트롤러+SecurityConfig만 명시.
 * (2) SecurityConfig가 요구하는 JwtAuthenticationFilter를 MockitoBean으로 채우고,
 *     체인을 통과시키는 스텁을 걸어야 필터 이후 authorizeHttpRequests 검증이
 *     정상 동작한다.
 */
@WebMvcTest(controllers = SeatController.class)
@ContextConfiguration(classes = {SeatController.class, SecurityConfig.class})
class SeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeatService seatService;
    @MockitoBean
    private SeatEmitterStore seatEmitterStore;
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
    @DisplayName("좌석 조회는 인증 없이도 가능하다")
    void anyoneCanGetSeats() throws Exception {
        given(seatService.getSeats("show-1")).willReturn(List.of());

        mockMvc.perform(get("/api/seats/show-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("인증된 사용자가 hold 요청하면 200을 반환한다")
    void holdSeatsReturnsOk() throws Exception {
        mockMvc.perform(post("/api/seats/show-1/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds":[1,2]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(seatService).holdSeats("show-1", List.of(1L, 2L), "user1");
    }

    @Test
    @DisplayName("인증 없이 hold 요청하면 401이 나온다")
    void unauthenticatedCannotHold() throws Exception {
        mockMvc.perform(post("/api/seats/show-1/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seatIds":[1,2]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user1")
    @DisplayName("인증된 사용자가 release 요청하면 200을 반환한다")
    void releaseSeatReturnsOk() throws Exception {
        mockMvc.perform(post("/api/seats/show-1/1/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(seatService).releaseSeat("show-1", 1L, "user1");
    }

    @Test
    @DisplayName("인증 없이 release 요청하면 401이 나온다")
    void unauthenticatedCannotRelease() throws Exception {
        mockMvc.perform(post("/api/seats/show-1/1/release"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SSE 스트림 요청은 인증 없이도 가능하고, seatEmitterStore.create()를 정확한 showId로 호출한다")
    void streamSeatsCreatesEmitterForShowId() throws Exception {
        SseEmitter emitter = new SseEmitter();
        given(seatEmitterStore.create("show-1")).willReturn(emitter);

        var mvcResult = mockMvc.perform(get("/api/seats/show-1/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // SseEmitter는 원래 "오래 열려있는 연결"이 목적이라, 아무 데이터도 안 보내고
        // 열어만 두면 비동기 작업이 "완료됐다"는 신호 자체가 안 생겨 asyncDispatch가
        // 무한정(타임아웃까지) 대기한다 — 직접 겪었다. 테스트에서는 emitter를 명시적으로
        // complete()시켜서 "이 요청은 여기서 끝났다"는 신호를 만들어줘야 한다.
        emitter.complete();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        verify(seatEmitterStore).create("show-1");
    }
}