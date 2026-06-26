package com.seatflow.show.domain;

import com.seatflow.common.event.show.SeatGradeType;
import java.math.BigDecimal;

public record SeatGrade(
        SeatGradeType grade,
        int capacity,
        BigDecimal price
) {}