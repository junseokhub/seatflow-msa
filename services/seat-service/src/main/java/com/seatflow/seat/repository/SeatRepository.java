package com.seatflow.seat.repository;

import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowId(String showId);
    List<Seat> findByShowIdAndStatus(String showId, SeatStatus status);

    // 멱등성: 이 공연 좌석이 이미 생성됐는지 확인 (중복 show.created 거르기)
    boolean existsByShowId(String showId);
}