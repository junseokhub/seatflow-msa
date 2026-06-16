package com.seatflow.seat.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.seat.dto.SeatResponse;
import com.seatflow.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/{showId}")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeats(@PathVariable String showId) {
        return ResponseEntity.ok(ApiResponse.ok(
                seatService.getSeats(showId).stream()
                        .map(SeatResponse::from)
                        .toList()
        ));
    }

    @PostMapping("/{showId}/{seatId}/hold")
    public ResponseEntity<ApiResponse<Void>> holdSeat(
            @PathVariable String showId,
            @PathVariable Long seatId,
            @RequestHeader("X-User-Id") String userId) {
        seatService.holdSeat(showId, seatId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{showId}/{seatId}/release")
    public ResponseEntity<ApiResponse<Void>> releaseSeat(
            @PathVariable String showId,
            @PathVariable Long seatId,
            @RequestHeader("X-User-Id") String userId) {
        seatService.releaseSeat(showId, seatId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}