package com.seatflow.reservation.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.reservation.dto.ReservationResponse;
import com.seatflow.reservation.exception.ReservationErrorCode;
import com.seatflow.reservation.service.ReservationService;
import com.seatflow.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 예매 조회 API.
 * 예매 "생성"은 좌석 점유(seat.held 이벤트)를 통해서만 일어난다. 좌석 가격 등 결제 근거를
 * 서버가 확정해야 하므로 클라이언트가 REST로 직접 예매를 만들지 않는다.
 * 취소는 CancelReservationController(Saga)로 분리돼 있다.
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                ReservationResponse.from(reservationService.getReservation(id))
        ));
    }

    /**
     * 본인 예매 목록 조회. path의 userId가 인증 주체와 일치해야 한다 — 그렇지 않으면
     * 로그인한 사용자가 다른 사람의 userId를 넣어 그 사람 예매 목록을 볼 수 있다.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getUserReservations(
            @PathVariable String userId,
            Authentication authentication) {
        if (!userId.equals(authentication.getName())) {
            throw new BusinessException(
                    ReservationErrorCode.RESERVATION_NOT_OWNED.getStatus().value(),
                    ReservationErrorCode.RESERVATION_NOT_OWNED.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok(
                reservationService.getUserReservations(userId).stream()
                        .map(ReservationResponse::from)
                        .toList()
        ));
    }

    // DELETE /api/reservations/{id} (cancelReservation) 삭제됨.
    // Saga 이전의 임시 취소 경로였다. 인증도 없고 좌석 반환/환불도 없이 상태만 바꿔
    // Saga를 완전히 우회하므로, 정식 취소 경로(POST /api/reservations/{id}/cancel,
    // CancelReservationController)로 완전히 대체한다.
}