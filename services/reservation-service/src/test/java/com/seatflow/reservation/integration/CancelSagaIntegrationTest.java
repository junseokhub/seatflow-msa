package com.seatflow.reservation.integration;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.common.test.composition.RedisContainerSupport;
import com.seatflow.reservation.domain.CancelSaga;
import com.seatflow.reservation.domain.CancelSagaStatus;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import com.seatflow.reservation.exception.ReservationErrorCode;
import com.seatflow.reservation.repository.CancelSagaRepository;
import com.seatflow.reservation.repository.ReservationRepository;
import com.seatflow.reservation.service.CancelSagaOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 취소 Saga의 전체 흐름을 진짜 MySQL 위에서 검증한다. 각 단계는 원래 비동기 (Kafka 이벤트를 매개로) 진행되지만,
 * 여기서는 컨슈머가 오케스트레이터의 메서드를 그대로 호출한다는 걸 이미 컨슈머 단위 테스트에서 확인했으므로,
 * 오케스트레이터의 메서드를 순서대로 직접 호출해 "Saga 상태 기계가 실제 DB 위에서 올바르게 흘러가는지"에 집중한다.
 * 진짜 Kafka까지 띄우는 end-to-end 검증은 Saga통합 테스트 범위로 미룬다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CancelSagaIntegrationTest implements MysqlContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerDefaultProperties(registry);
        MysqlContainerSupport.registerMysqlProperties(registry);
    }
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private CancelSagaRepository cancelSagaRepository;
    @Autowired
    private CancelSagaOrchestrator orchestrator;

    private Reservation confirmedReservation(LocalDateTime showDate) {
        Reservation reservation = Reservation.builder()
                .userId("user1").showId("show-1").seatId(1L)
                .amount(BigDecimal.valueOf(10000)).showDate(showDate)
                .status(ReservationStatus.PENDING)
                .build();
        Reservation saved = reservationRepository.save(reservation);
        // JpaRepository.save()는 그 자체로 트랜잭션이 열리고 끝나므로, 반환된 saved는 이후 detached 상태다.
        // confirm()으로 바꾼 상태는 메모리에만 있고 DB에는 반영되지 않으므로,
        // 명시적으로 다시 save해야 진짜로 CONFIRMED가 저장된다.
        saved.confirm();
        return reservationRepository.save(saved);
    }

    @Test
    @DisplayName("정상 경로: 취소 시작 -> 좌석반환 -> 환불완료 -> 예매가 최종 CANCELLED로 끝난다")
    void normalPathCompletesSuccessfully() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(15));

        orchestrator.startCancellation(reservation.getId(), "user1");
        Reservation afterStart = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(afterStart.getStatus()).isEqualTo(ReservationStatus.CANCELLING);

        CancelSaga saga = cancelSagaRepository.findByReservationId(reservation.getId()).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(CancelSagaStatus.STARTED);
        assertThat(saga.getRefundAmount()).isEqualByComparingTo("10000.00");   // 15일 전 -> 전액

        orchestrator.onSeatReleased(saga.getId(), reservation.getId());
        CancelSaga afterSeatReleased = cancelSagaRepository.findById(saga.getId()).orElseThrow();
        assertThat(afterSeatReleased.getStatus()).isEqualTo(CancelSagaStatus.SEAT_RELEASED);

        orchestrator.onPaymentRefunded(saga.getId(), reservation.getId());

        CancelSaga finalSaga = cancelSagaRepository.findById(saga.getId()).orElseThrow();
        Reservation finalReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(finalSaga.getStatus()).isEqualTo(CancelSagaStatus.COMPLETED);
        assertThat(finalReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("보상 경로: 환불 실패 시 좌석을 재점유하고 예매가 CONFIRMED로 원상복구된다")
    void compensationPathRestoresReservation() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(15));

        orchestrator.startCancellation(reservation.getId(), "user1");
        CancelSaga saga = cancelSagaRepository.findByReservationId(reservation.getId()).orElseThrow();

        orchestrator.onSeatReleased(saga.getId(), reservation.getId());
        orchestrator.onPaymentRefundFailed(saga.getId(), reservation.getId(), "카드 한도 초과");

        CancelSaga afterFailure = cancelSagaRepository.findById(saga.getId()).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(CancelSagaStatus.COMPENSATING);

        orchestrator.onSeatReservedCompensated(saga.getId(), reservation.getId());

        CancelSaga finalSaga = cancelSagaRepository.findById(saga.getId()).orElseThrow();
        Reservation finalReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(finalSaga.getStatus()).isEqualTo(CancelSagaStatus.FAILED);
        assertThat(finalReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);   // 원상복구
    }

    @Test
    @DisplayName("PENDING 예매는 Saga 없이 즉시 CANCELLED로 전이된다")
    void pendingReservationCancelsWithoutSaga() {
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .userId("user1").showId("show-1").seatId(1L)
                .amount(BigDecimal.valueOf(10000)).showDate(LocalDateTime.now().plusDays(15))
                .status(ReservationStatus.PENDING)
                .build());

        orchestrator.startCancellation(reservation.getId(), "user1");

        Reservation result = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        Optional<CancelSaga> saga = cancelSagaRepository.findByReservationId(reservation.getId());
        assertThat(saga).isEmpty();   // Saga 자체가 안 만들어짐
    }

    @Test
    @DisplayName("같은 예매에 취소를 두 번 시작하려 하면 두 번째는 unique 제약(DB 레벨 멱등성)으로 막힌다")
    void duplicateCancellationIsBlockedByUniqueConstraint() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(15));

        orchestrator.startCancellation(reservation.getId(), "user1");   // 첫 번째, 성공

        assertThatThrownBy(() -> orchestrator.startCancellation(reservation.getId(), "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ReservationErrorCode.CANCELLATION_ALREADY_IN_PROGRESS.getMessage());

        // Saga가 정확히 하나만 존재해야 한다.
        long sagaCount = cancelSagaRepository.findAll().stream()
                .filter(s -> s.getReservationId().equals(reservation.getId()))
                .count();
        assertThat(sagaCount).isEqualTo(1);
    }

    @Test
    @DisplayName("취소 가능 기간이 지난 예매는 취소를 시작할 수 없고 예매 상태도 그대로 유지된다")
    void cannotStartCancellationPastDeadline() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusHours(1));   // 곧 공연

        assertThatThrownBy(() -> orchestrator.startCancellation(reservation.getId(), "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ReservationErrorCode.CANCELLATION_DEADLINE_PASSED.getMessage());

        Reservation result = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);   // 그대로
    }

    @Test
    @DisplayName("정상 경로 완주 후, 지연 도착한 중복 seat.released 응답은 멱등하게 무시된다")
    void duplicateSeatReleasedAfterCompletionIsIgnored() {
        Reservation reservation = confirmedReservation(LocalDateTime.now().plusDays(15));
        orchestrator.startCancellation(reservation.getId(), "user1");
        CancelSaga saga = cancelSagaRepository.findByReservationId(reservation.getId()).orElseThrow();

        orchestrator.onSeatReleased(saga.getId(), reservation.getId());
        orchestrator.onPaymentRefunded(saga.getId(), reservation.getId());   // Saga 완료됨

        // 네트워크 재시도 등으로 seat.released가 뒤늦게 중복 도착한 상황을 재현
        orchestrator.onSeatReleased(saga.getId(), reservation.getId());

        CancelSaga finalSaga = cancelSagaRepository.findById(saga.getId()).orElseThrow();
        assertThat(finalSaga.getStatus()).isEqualTo(CancelSagaStatus.COMPLETED);   // 상태 안 바뀜
    }
}