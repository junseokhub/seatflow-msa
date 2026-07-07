package com.seatflow.reservation.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 취소 요청 진입점. Saga의 방아쇠만 당긴다(시작 명령만 발행하고 바로 응답).
 * 실제 취소 완료는 비동기로 진행되므로, 이 응답은 "취소가 시작됐다"를 의미하지
 * "취소가 끝났다"를 의미하지 않는다. 최종 상태는 예매 조회(GET)로 확인한다.
 */
@RestController
@RequiredArgsConstructor
public class CancelReservationController {

    private final CancelSagaOrchestrator cancelSagaOrchestrator;

    @PostMapping("/api/reservations/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            Authentication authentication) {
        String userId = authentication.getName();
        cancelSagaOrchestrator.startCancellation(id, userId);
        return ResponseEntity.accepted().body(ApiResponse.ok(null));
    }
}