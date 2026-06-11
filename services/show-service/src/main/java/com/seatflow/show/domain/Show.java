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
    // 기본적으로 리플렉션으로 객체 생성, final 있으면 기본 생성샂로 인스턴스 만든 뒤 필드 값에 주입하는 방식이 안되기 때문에 역직렬화에 문제 가능성
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
