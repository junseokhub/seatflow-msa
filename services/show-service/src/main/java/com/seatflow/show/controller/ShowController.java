package com.seatflow.show.controller;

import com.seatflow.common.response.ApiResponse;
import com.seatflow.show.domain.Show;
import com.seatflow.show.service.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Show>>> getShows() {
        return ResponseEntity.ok(ApiResponse.ok(showService.getShows()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Show>> getShow(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(showService.getShow(id)));
    }
}