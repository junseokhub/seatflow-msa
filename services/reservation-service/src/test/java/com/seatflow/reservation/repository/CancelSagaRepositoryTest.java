package com.seatflow.reservation.repository;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.reservation.domain.CancelSaga;
import com.seatflow.reservation.domain.CancelSagaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CancelSagaRepositoryTest implements MysqlContainerSupport {

     @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    private CancelSagaRepository cancelSagaRepository;

    private CancelSaga saga(Long reservationId) {
        return CancelSaga.builder()
                .reservationId(reservationId).userId("user1").seatId(1L).showId("show-1")
                .refundAmount(BigDecimal.valueOf(9000))
                .status(CancelSagaStatus.STARTED)
                .build();
    }

    @Test
    @DisplayName("findByReservationId()는 해당 예매의 Saga를 정확히 찾는다")
    void findByReservationIdFindsCorrectSaga() {
        cancelSagaRepository.save(saga(100L));

        Optional<CancelSaga> result = cancelSagaRepository.findByReservationId(100L);

        assertThat(result).isPresent();
        assertThat(result.get().getReservationId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("findByReservationId()는 Saga가 없으면 빈 Optional을 반환한다")
    void findByReservationIdReturnsEmptyWhenNotFound() {
        Optional<CancelSaga> result = cancelSagaRepository.findByReservationId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 reservationId로 중복 Saga를 저장하면 unique 제약 위반이 난다 (멱등성 보장)")
    void duplicateReservationIdViolatesUniqueConstraint() {
        cancelSagaRepository.save(saga(100L));
        cancelSagaRepository.flush();

        CancelSaga duplicate = saga(100L);   // 같은 reservationId

        org.junit.jupiter.api.Assertions.assertThrows(
                DataIntegrityViolationException.class,
                () -> {
                    cancelSagaRepository.save(duplicate);
                    cancelSagaRepository.flush();
                });
    }

    @Test
    @DisplayName("저장 시 @PrePersist가 status를 STARTED로 초기화한다")
    void prePersistInitializesStatus() {
        CancelSaga saga = CancelSaga.builder()
                .reservationId(100L).userId("user1").seatId(1L).showId("show-1")
                .refundAmount(BigDecimal.valueOf(9000))
                .status(CancelSagaStatus.COMPLETED)   // 빌더에서 뭘 넣든
                .build();

        CancelSaga saved = cancelSagaRepository.save(saga);

        assertThat(saved.getStatus()).isEqualTo(CancelSagaStatus.STARTED);   // @PrePersist가 이김
    }
}