package com.seatflow.show.dto;

import com.seatflow.show.domain.Show;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowResponse(
        String id,
        String title,
        String venue,
        LocalDateTime showDate,
        int totalSeats,
        BigDecimal price,
        LocalDateTime createdAt
) {
    public static ShowResponse from(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getVenue(),
                show.getShowDate(),
                show.getTotalSeats(),
                show.getPrice(),
                show.getCreatedAt()
        );
    }
}