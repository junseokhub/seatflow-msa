package com.seatflow.seat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SeatTest {

    private Seat newSeat() {
        return Seat.builder()
                .showId("show-1")
                .showDate(LocalDateTime.now().plusDays(7))
                .section("VIP")
                .seatRow("A")
                .number(1)
                .price(100000)
                .posX(0)
                .posY(0)
                .build();
    }

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("좌석을 RESERVED로 확정한다")
        void reservesSeat() {
            Seat seat = newSeat();

            seat.reserve();

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        }

        @Test
        @DisplayName("이미 RESERVED인 좌석에 다시 호출해도 멱등하게 무시된다")
        void reserveIsIdempotent() {
            Seat seat = newSeat();
            seat.reserve();

            seat.reserve();   // 중복 이벤트(reservation.confirmed 재수신)를 가정

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        }
    }

    @Nested
    @DisplayName("release()")
    class Release {

        @Test
        @DisplayName("RESERVED 좌석을 AVAILABLE로 되돌린다")
        void releasesReservedSeat() {
            Seat seat = newSeat();
            seat.reserve();

            seat.release();

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        }

        @Test
        @DisplayName("이미 AVAILABLE인 좌석에 다시 호출해도 멱등하게 무시된다")
        void releaseIsIdempotent() {
            Seat seat = newSeat();
            // 신규 좌석은 @PrePersist 이전엔 status가 null이라, 여기서는 release()
            // 자체의 멱등 가드(this.status == AVAILABLE일 때 무시)만 별도로 검증하기
            // 위해 reserve 후 release를 두 번 호출하는 방식으로 확인한다.
            seat.reserve();
            seat.release();

            seat.release();   // 중복 명령(취소 Saga 재시도)을 가정

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        }
    }
}