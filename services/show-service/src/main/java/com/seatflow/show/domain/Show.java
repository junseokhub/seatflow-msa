package com.seatflow.show.domain;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "shows")
@Getter
public class Show {

    @Id
    private String id;

    private String title;

    private String venue;

    private LocalDateTime showDate;

    private int totalSeats;

    private BigDecimal price;

    private LocalDateTime createdAt;

    @Builder
    private Show(String id, String title, String venue, LocalDateTime showDate, int totalSeats, BigDecimal price, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.venue = venue;
        this.showDate = showDate;
        this.totalSeats = totalSeats;
        this.price = price;
        this.createdAt = createdAt;
    }
}
