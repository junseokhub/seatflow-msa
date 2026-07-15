package com.seatflow.seat.repository;

import com.seatflow.common.test.composition.MysqlContainerSupport;
import com.seatflow.seat.domain.Seat;
import com.seatflow.seat.domain.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeatRepository의 커스텀 쿼리 메서드가 실제 DB 위에서 의도대로 필터링/조회하는지
 * 검증한다. 지금까지 서비스 계층 단위 테스트는 Repository를 Mock으로 대체해서
 * "호출됐는지"만 확인했을 뿐, "이 쿼리가 실제로 맞는 데이터를 가져오는지"는 검증한
 * 적이 없었다 — @DataJpaTest + TestContainers로 이 공백을 채운다.
 *
 * @AutoConfigureTestDatabase(replace = NONE)로 Spring Boot가 기본 제공하는
 * 인메모리 DB(H2) 자동 치환을 꺼야, MysqlContainerSupport가 지정한 진짜 MySQL이
 * 실제로 쓰인다 — 이걸 빠뜨리면 @DataJpaTest는 기본적으로 H2로 치환해버린다.
 */
@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeatRepositoryTest implements MysqlContainerSupport {

     @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDefaultJpaProperties(registry);
    }

    @Autowired
    private SeatRepository seatRepository;

    private Seat seat(String showId, String section, String seatRow, int number) {
        return Seat.builder()
                .showId(showId)
                .showDate(LocalDateTime.now().plusDays(7))
                .section(section)
                .seatRow(seatRow)
                .number(number)
                .price(100000)
                .posX(0)
                .posY(0)
                .build();
    }

    @Test
    @DisplayName("findByShowId()는 해당 공연의 좌석만 반환하고 다른 공연 좌석은 섞이지 않는다")
    void findByShowIdReturnsOnlyMatchingShow() {
        seatRepository.save(seat("show-1", "VIP", "A", 1));
        seatRepository.save(seat("show-1", "VIP", "A", 2));
        seatRepository.save(seat("show-2", "VIP", "A", 1));   // 다른 공연

        List<Seat> result = seatRepository.findByShowId("show-1");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(s -> s.getShowId().equals("show-1"));
    }

    @Test
    @DisplayName("findByShowId()는 좌석이 없는 공연이면 빈 리스트를 반환한다")
    void findByShowIdReturnsEmptyForUnknownShow() {
        List<Seat> result = seatRepository.findByShowId("unknown-show");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByShowIdAndStatus()는 상태로 정확히 필터링한다")
    void findByShowIdAndStatusFiltersCorrectly() {
        Seat available = seatRepository.save(seat("show-1", "VIP", "A", 1));
        Seat toReserve = seatRepository.save(seat("show-1", "VIP", "A", 2));
        toReserve.reserve();
        seatRepository.save(toReserve);

        List<Seat> availableSeats = seatRepository.findByShowIdAndStatus("show-1", SeatStatus.AVAILABLE);
        List<Seat> reservedSeats = seatRepository.findByShowIdAndStatus("show-1", SeatStatus.RESERVED);

        assertThat(availableSeats).extracting(Seat::getId).containsExactly(available.getId());
        assertThat(reservedSeats).extracting(Seat::getId).containsExactly(toReserve.getId());
    }

    @Test
    @DisplayName("existsByShowId()는 좌석이 하나라도 있으면 true, 없으면 false를 반환한다")
    void existsByShowIdReflectsActualPresence() {
        seatRepository.save(seat("show-1", "VIP", "A", 1));

        assertThat(seatRepository.existsByShowId("show-1")).isTrue();
        assertThat(seatRepository.existsByShowId("show-999")).isFalse();
    }

    @Test
    @DisplayName("같은 공연에 같은 (section, number) 좌석을 중복 저장하면 unique 제약 위반이 난다")
    void duplicateSeatViolatesUniqueConstraint() {
        seatRepository.save(seat("show-1", "VIP", "A", 1));
        seatRepository.flush();

        Seat duplicate = seat("show-1", "VIP", "A", 1);   // 같은 show, section, number

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> {
                    seatRepository.save(duplicate);
                    seatRepository.flush();
                });
    }
}