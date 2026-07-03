package com.seatflow.seat.service;

import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatHeldEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;
import com.seatflow.common.event.seat.SeatStatusChangedEvent;
import com.seatflow.seat.exception.SeatErrorCode;
import com.seatflow.seat.redis.SeatRedisProvider;
import com.seatflow.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatRedisProvider seatRedisProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationEventPublisher eventPublisher;

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

        eventPublisher.publishEvent(new SeatStatusChangedEvent(showId, seatId, "AVAILABLE"));
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

            // 3. 좌석별 점유 이벤트 발행 (가격을 함께 실어 reservation이 서버측 금액을 확보)
            // holdSeats의 "3. 좌석별 점유 이벤트 발행" 부분 교체
            // seatById 맵에서 각 좌석의 price + showDate를 함께 실어 발행
            Map<Long, Seat> seatById = seats.stream()
                    .collect(Collectors.toMap(Seat::getId, Function.identity()));
            for (Long seatId : seatIds) {
                Seat seat = seatById.get(seatId);
                BigDecimal price = BigDecimal.valueOf(seat.getPrice());
                kafkaTemplate.send(
                        EventTopic.SEAT_HELD,
                        userId,
                        EventEnvelope.of(EventTopic.SEAT_HELD, "seat-service",
                                new SeatHeldEvent(userId, showId, seatId, price, seat.getShowDate())));
            }
        } catch (RuntimeException e) {
            // 하나라도 어긋나면 방금 잡은 점유 전부 되돌린다 (보상)
            seatIds.forEach(id -> seatRedisProvider.releaseIfOwner(showId, id, userId));
            throw e;
        }

        log.info("Seats held: showId={}, seatIds={}, userId={}", showId, seatIds, userId);

        // 점유 성공 사실 발행 (SSE는 이 사실을 구독해서 알아서 전송)
        seatIds.forEach(id ->
                eventPublisher.publishEvent(new SeatStatusChangedEvent(showId, id, "HELD")));
    }

    // SeatService에 추가 (기존 필드에 SeatRepository, SeatRedisProvider 이미 있음)

    /**
     * 예매 확정(reservation.confirmed)으로 좌석을 확정 점유한다.
     * DB 상태를 RESERVED로 바꾸고(임시 점유 → 영구 확정), 남은 Redis hold 키를 정리한다.
     *
     * 멱등성: 같은 reservation.confirmed가 중복 도착해도 Seat.reserve()가 이미 RESERVED면
     * 무시한다(상태 전이 멱등). 좌석이 없으면 로깅 후 무시한다.
     */
    @Transactional
    public void reserveSeat(String showId, Long seatId, String userId) {
        Seat seat = seatRepository.findById(seatId).orElse(null);
        if (seat == null) {
            log.warn("Seat not found for reserve, skip: seatId={}", seatId);
            return;
        }

        seat.reserve();   // AVAILABLE/HELD → RESERVED (이미 RESERVED면 멱등 무시)

        // 확정됐으니 임시 점유(Redis hold)는 더 필요 없다. 정리한다.
        seatRedisProvider.release(showId, seatId);

        log.info("Seat reserved (confirmed): showId={}, seatId={}", showId, seatId);
    }
}