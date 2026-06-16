package com.seatflow.show.service.command;

import com.seatflow.show.dto.ShowRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateShowCommand(
        String title,
        String venue,
        LocalDateTime showDate,
        int totalSeats,
        BigDecimal price
) {
    public static CreateShowCommand from(ShowRequest request) {
        return new CreateShowCommand(
                request.title(),
                request.venue(),
                request.showDate(),
                request.totalSeats(),
                request.price()
        );
    }
}