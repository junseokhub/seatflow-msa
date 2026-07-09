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

    @Column(name = "show_date", nullable = false)
    private LocalDateTime showDate;

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

    @Column(name = "pos_x")
    private Integer posX;

    @Column(name = "pos_y")
    private Integer posY;

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
    private Seat(String showId, LocalDateTime showDate, String section, String seatRow,
                 int number, int price, Integer posX, Integer posY) {
        this.showId = showId;
        this.showDate = showDate;
        this.section = section;
        this.seatRow = seatRow;
        this.number = number;
        this.price = price;
        this.posX = posX;
        this.posY = posY;
    }

    /**
     * 결제 완료로 좌석을 확정 점유한다(→ RESERVED).
     * 임시 점유(Redis hold)를 영구 확정(DB)으로 넘기는 전이다.
     * 이미 RESERVED면 멱등하게 무시한다(reservation.confirmed 중복 수신 대비).
     */
    public void reserve() {
        if (this.status == SeatStatus.RESERVED) {
            return;   // 이미 확정 → 멱등 무시
        }
        this.status = SeatStatus.RESERVED;
    }

    /**
     * 예매 취소로 좌석을 다시 풀어준다(→ AVAILABLE).
     * 취소·환불 Saga의 좌석 반환 단계에서 호출한다.
     * 이미 AVAILABLE이면 멱등하게 무시한다.
     */
    public void release() {
        if (this.status == SeatStatus.AVAILABLE) {
            return;   // 이미 반환 → 멱등 무시
        }
        this.status = SeatStatus.AVAILABLE;
    }
}