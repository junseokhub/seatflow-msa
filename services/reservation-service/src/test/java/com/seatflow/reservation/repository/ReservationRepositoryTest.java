package com.seatflow.reservation.repository;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.reservation.domain.Reservation;
import com.seatflow.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryTest implements MysqlContainerSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private ReservationRepository reservationRepository;

    private Reservation reservation(String userId, String showId, Long seatId) {
        return Reservation.builder()
                .userId(userId).showId(showId).seatId(seatId)
                .amount(BigDecimal.valueOf(50000))
                .showDate(LocalDateTime.now().plusDays(10))
                .status(ReservationStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("findByUserId()는 해당 유저의 예매만 반환한다")
    void findByUserIdReturnsOnlyMatchingUser() {
        reservationRepository.save(reservation("user1", "show-1", 1L));
        reservationRepository.save(reservation("user1", "show-2", 2L));
        reservationRepository.save(reservation("user2", "show-1", 3L));   // 다른 유저

        List<Reservation> result = reservationRepository.findByUserId("user1");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getUserId().equals("user1"));
    }

    @Test
    @DisplayName("findByUserId()는 예매가 없는 유저면 빈 리스트를 반환한다")
    void findByUserIdReturnsEmptyForUnknownUser() {
        List<Reservation> result = reservationRepository.findByUserId("unknown-user");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByShowId()는 해당 공연의 예매만 반환한다")
    void findByShowIdReturnsOnlyMatchingShow() {
        reservationRepository.save(reservation("user1", "show-1", 1L));
        reservationRepository.save(reservation("user2", "show-1", 2L));
        reservationRepository.save(reservation("user1", "show-2", 3L));   // 다른 공연

        List<Reservation> result = reservationRepository.findByShowId("show-1");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getShowId().equals("show-1"));
    }

    @Test
    @DisplayName("각 예매는 서로 다른 reservationNumber(UUID)를 자동으로 갖는다")
    void eachReservationHasUniqueReservationNumber() {
        Reservation r1 = reservationRepository.save(reservation("user1", "show-1", 1L));
        Reservation r2 = reservationRepository.save(reservation("user1", "show-1", 2L));

        assertThat(r1.getReservationNumber()).isNotEqualTo(r2.getReservationNumber());
    }

    @Test
    @DisplayName("저장 시 @PrePersist가 status를 PENDING으로, createdAt/updatedAt을 채운다")
    void prePersistInitializesStatusAndTimestamps() {
        // 빌더에서 CONFIRMED로 지정해도, 진짜 저장을 거치면 @PrePersist가
        // 무조건 PENDING으로 덮어쓴다 — 이게 "빌더의 status 파라미터는 저장을
        // 안 하는 순수 단위 테스트에서만 의미가 있다"는 걸 실제로 증명하는 테스트다.
        Reservation reservation = Reservation.builder()
                .userId("user1").showId("show-1").seatId(1L)
                .amount(BigDecimal.valueOf(50000)).showDate(LocalDateTime.now().plusDays(10))
                .status(ReservationStatus.CONFIRMED)   // 빌더에서 뭘 넣든
                .build();

        Reservation saved = reservationRepository.save(reservation);

        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING);   // @PrePersist가 이김
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}