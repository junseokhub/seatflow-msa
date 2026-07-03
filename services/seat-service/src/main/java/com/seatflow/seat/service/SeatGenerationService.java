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
 * 멱등성: 같은 show.created가 중복 도착해도 (show_id, section, number) unique 제약이 막는다.
 * 좌석 생성은 배치(saveAll) INSERT이고, 공연당 한 번만 일어나는 저빈도 작업이다.
 * 그래서 커넥션을 하나 더 쓰는 부담(REQUIRES_NEW)이 실질적으로 문제되지 않고,
 * 오히려 배치 충돌을 독립 트랜잭션으로 통째로 격리하는 편이 안전하다.
 * 충돌이 나면 이 트랜잭션만 롤백되고 호출자(컨슈머)의 트랜잭션은 오염되지 않는다.
 *
 * (회원가입 createUser는 고빈도 단건이라 noRollbackFor + saveAndFlush로 커넥션을 아낀다.
 *  연산의 빈도와 배치 여부에 따라 멱등성 방식을 다르게 택한다.)
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class SeatGenerationService {

    private final SeatRepository seatRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createSeats(ShowCreatedEvent event) {
        String showId = event.showId();
        List<Seat> seats = buildSeats(event);

        try {
            seatRepository.saveAll(seats);
            log.info("Seats created: showId={}, count={}", showId, seats.size());
        } catch (DataIntegrityViolationException e) {
            // (show_id, section, number) unique 충돌 = 이미 생성된 공연(중복 이벤트) → 무시.
            // 그 외 정합성 위반(NOT NULL 등)은 재던져 롤백을 유도한다.
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
        for (ShowCreatedEvent.GradeSpec grade : event.grades()) {
            String section = grade.grade().name();
            for (int number = 1; number <= grade.capacity(); number++) {
                seats.add(Seat.builder()
                        .showId(event.showId())
                        .showDate(event.showDate())
                        .section(section)
                        .seatRow(section)
                        .number(number)
                        .price(grade.price().intValue())
                        .build());
            }
        }
        return seats;
    }
}