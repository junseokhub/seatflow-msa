package com.seatflow.seat.service;

import com.seatflow.common.event.seat.SeatStatusChangedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.outbox.jpa.OutboxAppender;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;
import com.seatflow.seat.exception.SeatErrorCode;
import com.seatflow.seat.redis.SeatRedisProvider;
import com.seatflow.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SeatService의 분기 로직을 Mockito로 검증한다.
 * 실제 필드 구성(seatRedisProvider, seatRepository, outboxAppender, eventPublisher)은 제공된 코드 스니펫에서 사용된 이름을 그대로 따랐다.
 * 실제 생성자 시그니처와 다르면 필드/생성자만 맞추면 된다.
 */
@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatRedisProvider seatRedisProvider;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private OutboxAppender outboxAppender;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SeatService seatService;

    @BeforeEach
    void setUp() {
        seatService = new SeatService(seatRepository, seatRedisProvider,  eventPublisher, outboxAppender);
    }

    private Seat availableSeat(Long id) {
        Seat seat = Seat.builder()
                .showId("show-1")
                .showDate(LocalDateTime.now().plusDays(7))
                .section("VIP")
                .seatRow("A")
                .number(1)
                .price(100000)
                .posX(0)
                .posY(0)
                .build();
        // id는 @GeneratedValue라 리플렉션 없이는 직접 못 채운다. 여기서는 findAllById
        // 목이 반환하는 리스트 순서만으로 테스트를 구성하고, id 값 자체보다
        // status/개수 검증에 집중한다.
        return seat;
    }

    @Nested
    @DisplayName("holdSeats()")
    class HoldSeats {

        @Test
        @DisplayName("Redis 게이트 획득 실패(이미 점유된 좌석 있음)면 SEAT_ALREADY_HELD 예외, DB 조회 자체를 안 한다")
        void throwsWhenGateAcquisitionFails() {
            List<Long> seatIds = List.of(1L, 2L);
            given(seatRedisProvider.holdAll("show-1", seatIds, "user1")).willReturn(false);

            assertThatThrownBy(() -> seatService.holdSeats("show-1", seatIds, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(SeatErrorCode.SEAT_ALREADY_HELD.getMessage());

            verify(seatRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("존재하지 않는 좌석이 섞여 있으면 SEAT_NOT_FOUND 예외를 던지고 Redis 게이트를 되돌린다")
        void throwsWhenSeatNotFoundAndRollsBackRedisGate() {
            List<Long> seatIds = List.of(1L, 2L);
            given(seatRedisProvider.holdAll("show-1", seatIds, "user1")).willReturn(true);
            given(seatRepository.findAllById(seatIds)).willReturn(List.of(availableSeat(1L)));   // 1개만 존재

            assertThatThrownBy(() -> seatService.holdSeats("show-1", seatIds, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(SeatErrorCode.SEAT_NOT_FOUND.getMessage());

            // 보상: 잡았던 게이트를 좌석마다 되돌린다.
            verify(seatRedisProvider).releaseIfOwner("show-1", 1L, "user1");
            verify(seatRedisProvider).releaseIfOwner("show-1", 2L, "user1");
        }

        @Test
        @DisplayName("좌석 중 하나라도 이미 RESERVED면 SEAT_ALREADY_RESERVED 예외를 던지고 Redis 게이트를 되돌린다")
        void throwsWhenAnySeatAlreadyReservedAndRollsBack() {
            List<Long> seatIds = List.of(1L, 2L);
            given(seatRedisProvider.holdAll("show-1", seatIds, "user1")).willReturn(true);
            Seat reserved = availableSeat(2L);
            reserved.reserve();
            given(seatRepository.findAllById(seatIds)).willReturn(List.of(availableSeat(1L), reserved));

            assertThatThrownBy(() -> seatService.holdSeats("show-1", seatIds, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(SeatErrorCode.SEAT_ALREADY_RESERVED.getMessage());

            verify(seatRedisProvider).releaseIfOwner("show-1", 1L, "user1");
            verify(seatRedisProvider).releaseIfOwner("show-1", 2L, "user1");
        }
    }

    @Nested
    @DisplayName("releaseSeat()")
    class ReleaseSeat {

        @Test
        @DisplayName("본인이 점유한 좌석이면 정상 해제되고 AVAILABLE 상태변경 이벤트가 발행된다")
        void releasesOwnedSeat() {
            given(seatRedisProvider.releaseIfOwner("show-1", 1L, "user1")).willReturn(1L);

            seatService.releaseSeat("show-1", 1L, "user1");

            verify(eventPublisher).publishEvent(new SeatStatusChangedEvent("show-1", 1L, "AVAILABLE"));
        }

        @Test
        @DisplayName("아무도 점유하지 않은 좌석이면 SEAT_NOT_HELD 예외")
        void throwsWhenNotHeld() {
            given(seatRedisProvider.releaseIfOwner("show-1", 1L, "user1")).willReturn(-1L);

            assertThatThrownBy(() -> seatService.releaseSeat("show-1", 1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(SeatErrorCode.SEAT_NOT_HELD.getMessage());
        }

        @Test
        @DisplayName("타인이 점유한 좌석이면 SEAT_HOLD_NOT_OWNED 예외")
        void throwsWhenOwnedBySomeoneElse() {
            given(seatRedisProvider.releaseIfOwner("show-1", 1L, "user1")).willReturn(0L);

            assertThatThrownBy(() -> seatService.releaseSeat("show-1", 1L, "user1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(SeatErrorCode.SEAT_HOLD_NOT_OWNED.getMessage());
        }
    }

    @Nested
    @DisplayName("reserveSeat()")
    class ReserveSeat {

        @Test
        @DisplayName("좌석을 RESERVED로 확정하고 Redis hold를 정리한다")
        void confirmsSeatAndClearsRedisHold() {
            Seat seat = availableSeat(1L);
            given(seatRepository.findById(1L)).willReturn(Optional.of(seat));

            seatService.reserveSeat("show-1", 1L, "user1");

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
            verify(seatRedisProvider).release("show-1", 1L);
        }

        @Test
        @DisplayName("존재하지 않는 좌석이면 조용히 무시하고 아무 것도 안 한다")
        void doesNothingWhenSeatNotFound() {
            given(seatRepository.findById(999L)).willReturn(Optional.empty());

            seatService.reserveSeat("show-1", 999L, "user1");

            verify(seatRedisProvider, never()).release(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("releaseSeatForCancellation()")
    class ReleaseSeatForCancellation {

        @Test
        @DisplayName("좌석을 AVAILABLE로 되돌리고 SEAT_RELEASED 이벤트를 Outbox에 남긴다")
        void releasesSeatAndAppendsOutboxEvent() {
            Seat seat = availableSeat(1L);
            seat.reserve();
            given(seatRepository.findById(1L)).willReturn(Optional.of(seat));

            seatService.releaseSeatForCancellation(100L, 200L, "show-1", 1L);

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("존재하지 않는 좌석이면 조용히 무시한다 (Outbox 기록도 안 함)")
        void doesNothingWhenSeatNotFound() {
            given(seatRepository.findById(999L)).willReturn(Optional.empty());

            seatService.releaseSeatForCancellation(100L, 200L, "show-1", 999L);

            verify(outboxAppender, never()).append(any(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("reserveSeatForCompensation()")
    class ReserveSeatForCompensation {

        @Test
        @DisplayName("좌석을 다시 RESERVED로 되돌리고(보상) SEAT_RESERVED_COMPENSATED 이벤트를 남긴다")
        void reReservesSeatAsCompensation() {
            Seat seat = availableSeat(1L);
            given(seatRepository.findById(1L)).willReturn(Optional.of(seat));

            seatService.reserveSeatForCompensation(100L, 200L, "show-1", 1L);

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
            verify(outboxAppender).append(any(), anyString(), anyString(), any());
        }
    }
}