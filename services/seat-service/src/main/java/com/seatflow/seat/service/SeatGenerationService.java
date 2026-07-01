package com.seatflow.seat.service;

import com.seatflow.common.event.show.ShowCreatedEvent;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatGenerationService {

    private final SeatRepository seatRepository;

// REQUIRES_NEW라 이 트랜잭션만 롤백되고 호출자(컨슈머)는 안전.
//  @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void createSeats(ShowCreatedEvent event) {
        String showId = event.showId();
        List<Seat> seats = buildSeats(event);

        try {
            seatRepository.saveAll(seats);
            log.info("Seats created: showId={}, count={}", showId, seats.size());
        } catch (DataIntegrityViolationException e) {
            // (show_id, section, number) unique 충돌 = 이미 생성된 공연(중복 이벤트) -> 무시.
            log.info("Seats already exist for show, skip (duplicate event): showId={}", showId);
        }
    }

    private List<Seat> buildSeats(ShowCreatedEvent event) {
        List<Seat> seats = new ArrayList<>();
        for (ShowCreatedEvent.GradeSpec grade : event.grades()) {
            String section = grade.grade().name();   // VIP/R/S
            for (int number = 1; number <= grade.capacity(); number++) {
                seats.add(Seat.builder()
                        .showId(event.showId())
                        .section(section)
                        .seatRow(section)            // 등급명을 행으로(단순). 좌석배치 고도화 시 분리
                        .number(number)
                        .price(grade.price().intValue())
                        .build());
            }
        }
        return seats;
    }
}