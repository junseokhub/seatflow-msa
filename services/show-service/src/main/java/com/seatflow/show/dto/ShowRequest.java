package com.seatflow.show.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShowRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @NotNull @Future LocalDateTime showDate,   // 과거 날짜로 공연 생성 방지
        @NotEmpty @Valid List<SeatGradeRequest> seatGrades
) {
    public record SeatGradeRequest(
            @NotBlank String grade,
            @NotNull @Positive Integer capacity,
            @NotNull @PositiveOrZero BigDecimal price
    ) {}
}