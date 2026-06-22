package com.seatflow.show.domain;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "shows")
@Getter
public class Show {

    @Id
    private String id;

    private String title;

    private String venue;

    private LocalDateTime showDate;

    // 등급별 정원·가격 구성 (문서 안에 중첩). 단일 totalSeats/price를 대체.
    private List<SeatGrade> seatGrades;

    private LocalDateTime createdAt;

    @Builder
    private Show(String id, String title, String venue, LocalDateTime showDate,
                 List<SeatGrade> seatGrades, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.venue = venue;
        this.showDate = showDate;
        this.seatGrades = seatGrades;
        this.createdAt = createdAt;
    }

    /** 전체 좌석 수가 필요하면 등급 정원 합으로 유도 (별도 필드로 중복 저장하지 않음) */
    public int totalSeats() {
        return seatGrades.stream().mapToInt(SeatGrade::getCapacity).sum();
    }
}