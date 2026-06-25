package com.seatflow.show.dto;

import com.seatflow.show.domain.SeatGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record ShowRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @NotNull LocalDateTime showDate,
        List<SeatGrade> seatGrades
) {}