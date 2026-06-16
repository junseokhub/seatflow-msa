package com.seatflow.reservation.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.reservation.dto.ReservationRequest;
import com.seatflow.reservation.dto.ReservationResponse;
import com.seatflow.reservation.service.ReservationService;
import com.seatflow.reservation.service.command.CreateReservationCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @RequestBody @Valid ReservationRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                ReservationResponse.from(reservationService.createReservation(
                        new CreateReservationCommand(userId, request.showId(), request.seatId())
                ))
        ));
    }

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
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @PathVariable Long id) {
        reservationService.cancelReservation(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}