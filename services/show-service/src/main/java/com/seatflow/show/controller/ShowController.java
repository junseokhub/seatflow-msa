package com.seatflow.show.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.show.dto.ShowRequest;
import com.seatflow.show.dto.ShowResponse;
import com.seatflow.show.service.ShowService;
import com.seatflow.show.service.command.CreateShowCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShowResponse>>> getShows() {
        return ResponseEntity.ok(ApiResponse.ok(
                showService.getShows().stream()
                        .map(ShowResponse::from)
                        .toList()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShowResponse>> getShow(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(ShowResponse.from(showService.getShow(id))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShowResponse>> createShow(
            @RequestBody @Valid ShowRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                ShowResponse.from(showService.createShow(CreateShowCommand.from(request)))
        ));
    }
}