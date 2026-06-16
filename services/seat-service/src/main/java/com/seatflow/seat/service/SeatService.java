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
        String holder = seatRedisProvider.getHolder(showId, seatId);

        if (holder == null) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_NOT_HELD.getStatus().value(),
                    SeatErrorCode.SEAT_NOT_HELD.getMessage()
            );
        }

        if (!holder.equals(userId)) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_HOLD_NOT_OWNED.getStatus().value(),
                    SeatErrorCode.SEAT_HOLD_NOT_OWNED.getMessage()
            );
        }

        seatRedisProvider.release(showId, seatId);
        log.info("Seat released: showId={}, seatId={}, userId={}", showId, seatId, userId);
    }

    @Transactional
    public void holdSeat(String showId, Long seatId, String userId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new BusinessException(
                        SeatErrorCode.SEAT_NOT_FOUND.getStatus().value(),
                        SeatErrorCode.SEAT_NOT_FOUND.getMessage()
                ));

        if (seat.getStatus() == SeatStatus.RESERVED) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_ALREADY_RESERVED.getStatus().value(),
                    SeatErrorCode.SEAT_ALREADY_RESERVED.getMessage()
            );
        }

        boolean success = seatRedisProvider.hold(showId, seatId, userId);
        if (!success) {
            throw new BusinessException(
                    SeatErrorCode.SEAT_ALREADY_HELD.getStatus().value(),
                    SeatErrorCode.SEAT_ALREADY_HELD.getMessage()
            );
        }

        kafkaTemplate.send(
                EventTopic.SEAT_HELD,
                userId,
                EventEnvelope.of(
                        EventTopic.SEAT_HELD,
                        "seat-service",
                        new SeatHeldEvent(userId, showId, seatId)
                )
        );

        log.info("Seat held: showId={}, seatId={}, userId={}", showId, seatId, userId);
    }
}