package com.seatflow.seat.service;

import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.seat.SeatHeldEvent;
import com.seatflow.common.event.seat.SeatReleasedEvent;
import com.seatflow.common.event.seat.SeatReservedCompensatedEvent;
import com.seatflow.common.event.seat.SeatStatusChangedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;
import com.seatflow.seat.exception.SeatErrorCode;
import com.seatflow.seat.redis.SeatRedisProvider;
import com.seatflow.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxAppender outboxAppender;

    private static final String SOURCE = "seat-service";

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

            // holdSeats의 "3. 좌석별 점유 이벤트 발행" 부분을 Outbox 적재로 교체.
            // 기존엔 kafkaTemplate.send로 직접 발행(dual-write). 이제 Outbox에 적재하고
            // 공통 OutboxScheduler가 발행을 보장한다. payment·reservation과 같은 패턴.
            //
            // 필드에 OutboxRepository, ObjectMapper(kafkaObjectMapper) 주입 필요.
            // (kafkaTemplate은 SSE 등 다른 용도로 남을 수 있으니 유지 여부는 사용처 확인)

            // 3. 좌석별 점유 이벤트 발행 (가격·공연일을 함께 실어 reservation이 서버측 값 확보)
            //    Outbox에 적재 → 스케줄러가 발행 보장 (좌석 검증과 같은 트랜잭션)
            Map<Long, Seat> seatById = seats.stream()
                    .collect(Collectors.toMap(Seat::getId, Function.identity()));
            for (Long seatId : seatIds) {
                Seat seat = seatById.get(seatId);
                BigDecimal price = BigDecimal.valueOf(seat.getPrice());
                outboxAppender.append(EventTopic.SEAT_HELD, SOURCE, userId,
                        new SeatHeldEvent(userId, showId, seatId, price, seat.getShowDate()));
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

    /**
     * 취소 Saga의 좌석 반환 명령 처리. DB 상태를 AVAILABLE로 되돌리고 응답을 발행한다.
     * seat.release()가 이미 멱등(AVAILABLE이면 무시)이라 중복 명령도 안전하다.
     */
    @Transactional
    public void releaseSeatForCancellation(Long sagaId, Long reservationId, String showId, Long seatId) {
        Seat seat = seatRepository.findById(seatId).orElse(null);
        if (seat == null) {
            log.warn("Seat not found for cancel release, skip: seatId={}", seatId);
            return;
        }

        seat.release();   // RESERVED → AVAILABLE (이미 AVAILABLE이면 멱등 무시)

        outboxAppender.append(EventTopic.SEAT_RELEASED, SOURCE, String.valueOf(seatId),
                new SeatReleasedEvent(sagaId, reservationId, seatId));

        log.info("Seat released for cancellation: sagaId={}, seatId={}", sagaId, seatId);
    }

    /**
     * 취소 Saga 보상: 환불 실패로 되돌아온 좌석을 다시 점유(RESERVED)시키고 응답을 발행한다.
     * seat.reserve()가 이미 멱등이라 중복 명령도 안전하다.
     */
    @Transactional
    public void reserveSeatForCompensation(Long sagaId, Long reservationId, String showId, Long seatId) {
        Seat seat = seatRepository.findById(seatId).orElse(null);
        if (seat == null) {
            log.warn("Seat not found for compensation reserve, skip: seatId={}", seatId);
            return;
        }

        seat.reserve();   // AVAILABLE → RESERVED (이미 RESERVED면 멱등 무시)

        outboxAppender.append(EventTopic.SEAT_RESERVED_COMPENSATED, SOURCE, String.valueOf(seatId),
                new SeatReservedCompensatedEvent(sagaId, reservationId, seatId));

        log.info("Seat reserved back (compensated): sagaId={}, seatId={}", sagaId, seatId);
    }
}