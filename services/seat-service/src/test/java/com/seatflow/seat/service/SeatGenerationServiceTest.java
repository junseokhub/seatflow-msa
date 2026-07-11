package com.seatflow.seat.service;

import com.seatflow.common.event.show.ShowCreatedEvent;
import com.seatflow.common.event.show.SeatGradeType;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * show.created 이벤트를 받아 좌석을 등급별로 배치하는 로직을 검증한다. 실제 DB 없이
 * saveAll에 전달되는 좌석 목록만 캡처해서, 개수/행 나눔/좌표 계산이 맞는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class SeatGenerationServiceTest {

    @Mock
    private SeatRepository seatRepository;

    private SeatGenerationService seatGenerationService;

    @BeforeEach
    void setUp() {
        seatGenerationService = new SeatGenerationService(seatRepository);
    }

    @Nested
    @DisplayName("좌석 개수/배치")
    class SeatLayout {

        @Test
        @DisplayName("정확히 20개씩 나뉘어 정원(capacity)만큼 좌석이 생성된다")
        void createsExactCapacityCount() {
            ShowCreatedEvent event = new ShowCreatedEvent(
                    "show-1", LocalDateTime.now().plusDays(7),
                    List.of(new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 45, BigDecimal.valueOf(100000))));

            seatGenerationService.createSeats(event);

            ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
            verify(seatRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(45);
        }

        @Test
        @DisplayName("정원이 20의 배수가 아니면 마지막 행에 나머지만큼만 생성된다")
        void lastRowHasRemainderOnly() {
            // 45석 = 20 + 20 + 5, 마지막 행(C)은 5석이어야 한다.
            ShowCreatedEvent event = new ShowCreatedEvent(
                    "show-1", LocalDateTime.now().plusDays(7),
                    List.of(new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 45, BigDecimal.valueOf(100000))));

            seatGenerationService.createSeats(event);

            ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
            verify(seatRepository).saveAll(captor.capture());
            long lastRowCount = captor.getValue().stream()
                    .filter(s -> s.getSeatRow().equals("C"))
                    .count();
            assertThat(lastRowCount).isEqualTo(5);
        }

        @Test
        @DisplayName("여러 등급이 있으면 등급 간 gap만큼 posY가 떨어져서 배치된다")
        void multipleGradesHaveGapBetweenSections() {
            // VIP 20석(1행) + gap(2) + R 20석(1행)
            // VIP posY: 0, R posY: 1(VIP 1행) + 2(gap) = 3
            ShowCreatedEvent event = new ShowCreatedEvent(
                    "show-1", LocalDateTime.now().plusDays(7),
                    List.of(
                            new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 20, BigDecimal.valueOf(100000)),
                            new ShowCreatedEvent.GradeSpec(SeatGradeType.R, 20, BigDecimal.valueOf(50000))
                    ));

            seatGenerationService.createSeats(event);

            ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
            verify(seatRepository).saveAll(captor.capture());
            List<Seat> seats = captor.getValue();

            int vipMaxPosY = seats.stream().filter(s -> s.getSection().equals("VIP"))
                    .mapToInt(Seat::getPosY).max().orElseThrow();
            int rMinPosY = seats.stream().filter(s -> s.getSection().equals("R"))
                    .mapToInt(Seat::getPosY).min().orElseThrow();

            assertThat(vipMaxPosY).isEqualTo(0);
            assertThat(rMinPosY).isEqualTo(3);   // 0(VIP 1행) + 1 + gap(2)
        }

        @Test
        @DisplayName("각 좌석의 price는 해당 등급의 price로 정확히 채워진다")
        void priceMatchesGradeSpec() {
            ShowCreatedEvent event = new ShowCreatedEvent(
                    "show-1", LocalDateTime.now().plusDays(7),
                    List.of(new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 5, BigDecimal.valueOf(150000))));

            seatGenerationService.createSeats(event);

            ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
            verify(seatRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).allMatch(s -> s.getPrice() == 150000);
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("중복 show.created(unique 제약 위반)는 예외를 삼키고 조용히 종료한다")
        void duplicateEventIsSwallowedSilently() throws Exception {
            ShowCreatedEvent event = new ShowCreatedEvent(
                    "show-1", LocalDateTime.now().plusDays(7),
                    List.of(new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));

            SQLException sqlEx = new SQLException("Duplicate entry", "23000", 1062);
            given(seatRepository.saveAll(anyList()))
                    .willThrow(new DataIntegrityViolationException("duplicate", sqlEx));

            assertThatCode(() -> seatGenerationService.createSeats(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("1062(중복)가 아닌 다른 SQL 에러는 그대로 예외를 던진다")
        void nonDuplicateSqlErrorIsRethrown() {
            ShowCreatedEvent event = new ShowCreatedEvent(
                    "show-1", LocalDateTime.now().plusDays(7),
                    List.of(new ShowCreatedEvent.GradeSpec(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));

            SQLException sqlEx = new SQLException("Some other error", "HY000", 1064);
            given(seatRepository.saveAll(anyList()))
                    .willThrow(new DataIntegrityViolationException("other", sqlEx));

            org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                    () -> seatGenerationService.createSeats(event));
        }
    }
}