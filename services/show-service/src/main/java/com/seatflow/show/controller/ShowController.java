package com.seatflow.show.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.show.dto.ShowRequest;
import com.seatflow.show.dto.ShowResponse;
import com.seatflow.show.service.ShowService;
import com.seatflow.show.service.command.CreateShowCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /**
     * 공연 생성은 관리자만 할 수 있다. JWT의 role 클레임이 ADMIN이어야 통과한다.
     * 일반 사용자의 요청은 인증까지는 통과해도 여기서 403으로 막힌다.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShowResponse>> createShow(
            @RequestBody @Valid ShowRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                ShowResponse.from(showService.createShow(CreateShowCommand.from(request)))
        ));
    }
}