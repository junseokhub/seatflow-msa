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
    public void holdSeat(String showId, Long seatId, String userId) {
        // 1. Redis 점유를 먼저 잡는다. 원자적 게이트, 동시 점유 배제
        boolean acquired = seatRedisProvider.hold(showId, seatId, userId);
        if (!acquired) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_ALREADY_HELD.getStatus().value(),
                    SeatErrorCode.SEAT_ALREADY_HELD.getMessage());
        }

        try {
            // 2. 점유를 쥔 상태에서 DB로 '판매 완료' 검증
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new BusinessException(
                            SeatErrorCode.SEAT_NOT_FOUND.getStatus().value(),
                            SeatErrorCode.SEAT_NOT_FOUND.getMessage()));

            if (seat.getStatus() == SeatStatus.RESERVED) {
                throw new BusinessException(
                        SeatErrorCode.SEAT_ALREADY_RESERVED.getStatus().value(),
                        SeatErrorCode.SEAT_ALREADY_RESERVED.getMessage());
            }

            // 3. 이벤트 발행 (seat dual-write는 추후 outbox로)
            kafkaTemplate.send(
                    EventTopic.SEAT_HELD,
                    userId,
                    EventEnvelope.of(EventTopic.SEAT_HELD, "seat-service",
                            new SeatHeldEvent(userId, showId, seatId)));

        } catch (RuntimeException e) {
            // 검증 실패 / 발행 실패 -> 방금 잡은 점유를 되돌린다 (보상)
            seatRedisProvider.releaseIfOwner(showId, seatId, userId);
            throw e;
        }

        log.info("Seat held: showId={}, seatId={}, userId={}", showId, seatId, userId);
    }
}