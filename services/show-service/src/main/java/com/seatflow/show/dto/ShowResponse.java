package com.seatflow.show.dto;

import com.seatflow.show.domain.SeatGrade;
import com.seatflow.show.domain.Show;

import java.time.LocalDateTime;
import java.util.List;

public record ShowResponse(
        String id,
        String title,
        String venue,
        LocalDateTime showDate,
        List<SeatGrade> seatGrades,
        LocalDateTime createdAt
) {
    public static ShowResponse from(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getVenue(),
                show.getShowDate(),
                show.getSeatGrades(),
                show.getCreatedAt()
        );
    }
}