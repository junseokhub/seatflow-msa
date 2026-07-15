package com.seatflow.common.event.show;

import com.seatflow.common.event.VersionedEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShowCreatedEvent(
        String showId,
        LocalDateTime showDate,
        List<GradeSpec> grades
) implements VersionedEvent {
    public record GradeSpec(
            SeatGradeType grade,
            int capacity,
            BigDecimal price
    ) {
    }

    @Override
    public String eventVersion() {
        return "1.0";
    }
}