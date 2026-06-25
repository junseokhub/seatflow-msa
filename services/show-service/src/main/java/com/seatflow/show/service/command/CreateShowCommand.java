package com.seatflow.show.service.command;

import com.seatflow.show.domain.SeatGrade;
import com.seatflow.show.dto.ShowRequest;

import java.time.LocalDateTime;
import java.util.List;

public record CreateShowCommand(
        String title,
        String venue,
        LocalDateTime showDate,
        List<SeatGrade> seatGrades
) {
    public static CreateShowCommand from(ShowRequest request) {
        return new CreateShowCommand(
                request.title(),
                request.venue(),
                request.showDate(),
                request.seatGrades()
        );
    }
}