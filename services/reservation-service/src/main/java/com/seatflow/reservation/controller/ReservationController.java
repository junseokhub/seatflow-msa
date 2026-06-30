package com.seatflow.reservation.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.reservation.dto.ReservationResponse;
import com.seatflow.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 예매 조회/취소 API.
 * 예매 "생성"은 좌석 점유(seat.held 이벤트)를 통해서만 일어난다. 좌석 가격 등 결제 근거를
 * 서버가 확정해야 하므로 클라이언트가 REST로 직접 예매를 만들지 않는다.
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

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getUserReservations(
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                reservationService.getUserReservations(userId).stream()
                        .map(ReservationResponse::from)
                        .toList()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(@PathVariable Long id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}