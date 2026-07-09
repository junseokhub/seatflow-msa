package com.seatflow.seat.service;

import com.seatflow.common.event.show.ShowCreatedEvent;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * show.created를 받아 등급별 좌석을 생성한다.
 *
 * 좌석 배치:
 *   - 등급(VIP/R/S)이 section이 된다.
 *   - 등급 내 좌석을 SEATS_PER_ROW씩 끊어 행(A/B/C…)을 만든다.
 *   - seatRow: 행 레이블(A, B, ..., Z, AA, AB, ...)
 *   - number: 행 안에서의 열 번호(1 ~ SEATS_PER_ROW)
 *   - posX: 열 인덱스(0-base), posY: 공연 전체 기준 행 인덱스(0-base, 등급별 gap 포함)
 *
 * 멱등성: (show_id, section, number) unique 제약이 중복 이벤트를 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatGenerationService {

    private static final int SEATS_PER_ROW = 20;
    private static final int SECTION_GAP   = 2; // 등급 간 빈 행 수

    private final SeatRepository seatRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createSeats(ShowCreatedEvent event) {
        String showId = event.showId();
        List<Seat> seats = buildSeats(event);

        try {
            seatRepository.saveAll(seats);
            log.info("Seats created: showId={}, count={}", showId, seats.size());
        } catch (DataIntegrityViolationException e) {
            Throwable cause = e.getRootCause();
            if (cause instanceof SQLException sqlEx && sqlEx.getErrorCode() == 1062) {
                log.info("Seats already exist for show, skip (duplicate event): showId={}", showId);
                return;
            }
            throw e;
        }
    }

    private List<Seat> buildSeats(ShowCreatedEvent event) {
        List<Seat> seats = new ArrayList<>();
        int globalRow = 0; // 공연 전체 기준 행 Y 인덱스

        for (ShowCreatedEvent.GradeSpec grade : event.grades()) {
            String section = grade.grade().name(); // "VIP", "R", "S"
            int capacity   = grade.capacity();
            int price      = grade.price().intValue();
            int totalRows  = (int) Math.ceil((double) capacity / SEATS_PER_ROW);

            for (int rowIdx = 0; rowIdx < totalRows; rowIdx++) {
                String seatRow    = rowLabel(rowIdx);
                int seatsInRow    = (rowIdx < totalRows - 1)
                        ? SEATS_PER_ROW
                        : capacity - rowIdx * SEATS_PER_ROW; // 마지막 행 나머지
                int posY = globalRow + rowIdx;

                for (int col = 0; col < seatsInRow; col++) {
                    int seatNumber = rowIdx * SEATS_PER_ROW + col + 1; // 1-base 전체 번호
                    seats.add(Seat.builder()
                            .showId(event.showId())
                            .showDate(event.showDate())
                            .section(section)
                            .seatRow(seatRow)
                            .number(col + 1)  // 행 안에서 1-base 번호
                            .price(price)
                            .posX(col)
                            .posY(posY)
                            .build());
                }
            }

            globalRow += totalRows + SECTION_GAP;
        }
        return seats;
    }

    /** 행 인덱스(0-base) → 레이블: A, B, ..., Z, AA, AB, ... */
    private static String rowLabel(int idx) {
        StringBuilder sb = new StringBuilder();
        do {
            sb.insert(0, (char) ('A' + idx % 26));
            idx = idx / 26 - 1;
        } while (idx >= 0);
        return sb.toString();
    }
}
