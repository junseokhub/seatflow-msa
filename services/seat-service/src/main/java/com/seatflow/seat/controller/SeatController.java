package com.seatflow.seat.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.seat.dto.HoldSeatsRequest;
import com.seatflow.seat.dto.SeatResponse;
import com.seatflow.seat.service.SeatService;
import com.seatflow.seat.sse.SeatEmitterStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;
    private final SeatEmitterStore seatEmitterStore;


    @GetMapping("/{showId}")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeats(@PathVariable String showId) {
        return ResponseEntity.ok(ApiResponse.ok(
                seatService.getSeats(showId).stream()
                        .map(SeatResponse::from)
                        .toList()
        ));
    }

    @PostMapping("/{showId}/hold")
    public ResponseEntity<ApiResponse<Void>> holdSeats(
            @PathVariable String showId,
            @RequestBody HoldSeatsRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        seatService.holdSeats(showId, request.seatIds(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{showId}/{seatId}/release")
    public ResponseEntity<ApiResponse<Void>> releaseSeat(
            @PathVariable String showId,
            @PathVariable Long seatId,
            Authentication authentication) {
        String userId = authentication.getName();
        seatService.releaseSeat(showId, seatId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping(value = "/{showId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeats(@PathVariable String showId) {
        return seatEmitterStore.create(showId);
    }
}