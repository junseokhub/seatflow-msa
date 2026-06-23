package com.seatflow.show.domain;

import com.seatflow.common.event.show.SeatGradeType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class SeatGrade {

    private SeatGradeType grade;       // "VIP", "R", "S" 등
    private int capacity;       // 해당 등급 정원
    private BigDecimal price;   // 해당 등급 가격

    @Builder
    private SeatGrade(SeatGradeType grade, int capacity, BigDecimal price) {
        this.grade = grade;
        this.capacity = capacity;
        this.price = price;
    }
}