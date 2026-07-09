package com.seatflow.reservation.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 취소 요청 진입점. PENDING/CONFIRMED 분기는 CancelSagaOrchestrator가 담당한다.
 * PENDING 동기 완료 → 200 OK, CONFIRMED 비동기 Saga 시작 → 202 Accepted는
 * 오케스트레이터가 반환 값 없이 처리하므로 컨트롤러는 항상 202를 반환한다.
 * (PENDING 취소는 즉시 완료지만 클라이언트는 마이페이지로 이동하면 확인 가능)
 */
@RestController
@RequiredArgsConstructor
public class CancelReservationController {

    private final CancelSagaOrchestrator cancelSagaOrchestrator;

    @PostMapping("/api/reservations/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            Authentication authentication) {
        cancelSagaOrchestrator.startCancellation(id, authentication.getName());
        return ResponseEntity.accepted().body(ApiResponse.ok(null));
    }
}
