package com.seatflow.common.event.show;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShowCreatedEvent(
        String showId,
        LocalDateTime showDate,
        List<GradeSpec> grades
) {
    public record GradeSpec(
            SeatGradeType grade,
            int capacity,
            BigDecimal price
    ) {
    }
}