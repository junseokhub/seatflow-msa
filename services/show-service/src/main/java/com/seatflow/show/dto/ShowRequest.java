package com.seatflow.show.dto;

import com.seatflow.common.event.show.SeatGradeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공연 생성 요청. grade를 처음부터 SeatGradeType(enum)으로 받는다 — String으로 받아
 * 서비스 계층에서 valueOf() 변환하면, 잘못된 값이 IllegalArgumentException으로
 * 터져 500이 될 위험이 있다. Jackson이 역직렬화 단계에서 걸러주면 그 값 자체가
 * 안 맞을 때 400(요청 파싱 실패)으로 명확하게 응답된다.
 */
public record ShowRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @NotNull @Future LocalDateTime showDate,
        @NotEmpty @Valid List<SeatGradeRequest> seatGrades
) {
    public record SeatGradeRequest(
            @NotNull SeatGradeType grade,
            @NotNull @Positive Integer capacity,
            @NotNull @PositiveOrZero BigDecimal price
    ) {}
}