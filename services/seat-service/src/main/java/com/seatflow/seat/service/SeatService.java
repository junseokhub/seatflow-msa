package com.seatflow.seat.service;

import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatHeldEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;
import com.seatflow.seat.exception.SeatErrorCode;
import com.seatflow.seat.redis.SeatRedisProvider;
import com.seatflow.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatRedisProvider seatRedisProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(readOnly = true)
    public List<Seat> getSeats(String showId) {
        return seatRepository.findByShowId(showId);
    }

    @Transactional
    public void releaseSeat(String showId, Long seatId, String userId) {
        long result = seatRedisProvider.releaseIfOwner(showId, seatId, userId);

        if (result == -1) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_NOT_HELD.getStatus().value(),
                    SeatErrorCode.SEAT_NOT_HELD.getMessage());
        }
        if (result == 0) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_HOLD_NOT_OWNED.getStatus().value(),
                    SeatErrorCode.SEAT_HOLD_NOT_OWNED.getMessage());
        }

        log.info("Seat released: showId={}, seatId={}, userId={}", showId, seatId, userId);
    }

    @Transactional
    public void holdSeats(String showId, List<Long> seatIds, String userId) {
        // 1. 멀티 좌석 게이트를 먼저 잡는다.(원자적)
        boolean acquired = seatRedisProvider.holdAll(showId, seatIds, userId);
        if (!acquired) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_ALREADY_HELD.getStatus().value(),
                    SeatErrorCode.SEAT_ALREADY_HELD.getMessage());
        }

        try {
            // 2. 점유를 쥔 상태에서 좌석들이 이미 팔렸는지 DB로 검증
            List<Seat> seats = seatRepository.findAllById(seatIds);

            if (seats.size() != seatIds.size()) { // 존재하지 않는 좌석이 섞임
                throw new BusinessException(
                        SeatErrorCode.SEAT_NOT_FOUND.getStatus().value(),
                        SeatErrorCode.SEAT_NOT_FOUND.getMessage());
            }
            boolean anyReserved = seats.stream()
                    .anyMatch(s -> s.getStatus() == SeatStatus.RESERVED);
            if (anyReserved) {
                throw new BusinessException(
                        SeatErrorCode.SEAT_ALREADY_RESERVED.getStatus().value(),
                        SeatErrorCode.SEAT_ALREADY_RESERVED.getMessage());
            }

            // 3. 좌석별 점유 이벤트 발행
            for (Long seatId : seatIds) {
                kafkaTemplate.send(
                        EventTopic.SEAT_HELD,
                        userId,
                        EventEnvelope.of(EventTopic.SEAT_HELD, "seat-service",
                                new SeatHeldEvent(userId, showId, seatId)));
            }

        } catch (RuntimeException e) {
            // 하나라도 어긋나면 방금 잡은 점유 전부 되돌린다 (보상)
            seatIds.forEach(id -> seatRedisProvider.releaseIfOwner(showId, id, userId));
            throw e;
        }

        log.info("Seats held: showId={}, seatIds={}, userId={}", showId, seatIds, userId);
    }
}