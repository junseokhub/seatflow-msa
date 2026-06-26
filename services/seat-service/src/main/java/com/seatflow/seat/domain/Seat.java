package com.seatflow.seat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "seats",
        // 멱등성: 같은 공연의 같은 (등급=section, 번호) 좌석은 하나뿐.
        // show.created가 중복 도착해도 INSERT 충돌로 중복 생성이 막힌다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seat_show_section_number",
                columnNames = {"show_id", "section", "number"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private String showId;

    @Column(nullable = false)
    private String section;     // 등급(VIP/R/S)을 section으로 매핑

    @Column(nullable = false)
    private String seatRow;

    @Column(nullable = false)
    private int number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = SeatStatus.AVAILABLE;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    private Seat(String showId, String section, String seatRow, int number, int price) {
        this.showId = showId;
        this.section = section;
        this.seatRow = seatRow;
        this.number = number;
        this.price = price;
    }
}