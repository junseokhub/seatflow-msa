package com.seatflow.show.repository;

import com.seatflow.common.event.show.SeatGradeType;
import com.seatflow.common.test.composition.MongoContainerSupport;
import com.seatflow.show.domain.SeatGrade;
import com.seatflow.show.domain.Show;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ShowRepositoryTest implements MongoContainerSupport {

    @Autowired
    private ShowRepository showRepository;

    @BeforeEach
    void cleanUp() {
        showRepository.deleteAll();
    }

    private Show show(String title) {
        return Show.builder()
                .title(title).venue("공연장")
                .showDate(LocalDateTime.of(2026, 12, 25, 19, 0))
                .seatGrades(List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("save() 후 반환된 객체에는 MongoDB가 생성한 id가 채워져 있다")
    void saveReturnsObjectWithGeneratedId() {
        Show saved = showRepository.save(show("공연1"));

        assertThat(saved.getId()).isNotBlank();
    }

    @Test
    @DisplayName("findById()로 저장된 공연을 정확히 찾는다")
    void findByIdFindsCorrectShow() {
        Show saved = showRepository.save(show("공연1"));

        Optional<Show> result = showRepository.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("공연1");
    }

    @Test
    @DisplayName("findAll()은 저장된 모든 공연을 반환한다")
    void findAllReturnsAllShows() {
        showRepository.save(show("공연1"));
        showRepository.save(show("공연2"));

        List<Show> result = showRepository.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("existsById()는 존재 여부를 정확히 반환한다")
    void existsByIdReflectsActualPresence() {
        Show saved = showRepository.save(show("공연1"));

        assertThat(showRepository.existsById(saved.getId())).isTrue();
        assertThat(showRepository.existsById("nonexistent-id")).isFalse();
    }

    @Test
    @DisplayName("deleteById() 후에는 findById()가 빈 결과를 반환한다")
    void deleteByIdRemovesShow() {
        Show saved = showRepository.save(show("공연1"));

        showRepository.deleteById(saved.getId());

        assertThat(showRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("seatGrades 리스트가 정확히 저장되고 조회된다 (임베디드 도큐먼트)")
    void seatGradesArePersistedCorrectly() {
        Show saved = showRepository.save(show("공연1"));

        Show result = showRepository.findById(saved.getId()).orElseThrow();

        assertThat(result.getSeatGrades()).hasSize(1);
        assertThat(result.getSeatGrades().get(0).grade()).isEqualTo(SeatGradeType.VIP);
        assertThat(result.getSeatGrades().get(0).capacity()).isEqualTo(10);
    }
}