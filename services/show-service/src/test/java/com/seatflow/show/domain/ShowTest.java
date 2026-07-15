package com.seatflow.show.domain;

import com.seatflow.common.event.show.SeatGradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShowTest {

    private Show newShow() {
        return Show.builder()
                .title("원제목")
                .venue("원공연장")
                .showDate(LocalDateTime.of(2026, 12, 25, 19, 0))
                .seatGrades(List.of(
                        new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000)),
                        new SeatGrade(SeatGradeType.R, 20, BigDecimal.valueOf(50000))
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("totalSeats()는 등급별 정원의 합을 반환한다")
    void totalSeatsSumsAllGradeCapacities() {
        Show show = newShow();

        assertThat(show.totalSeats()).isEqualTo(30);   // 10 + 20
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("모든 필드를 다 넘기면 전부 갱신된다")
        void updatesAllFieldsWhenAllProvided() {
            Show show = newShow();
            LocalDateTime newDate = LocalDateTime.of(2027, 1, 1, 20, 0);

            show.update("새제목", "새공연장", newDate);

            assertThat(show.getTitle()).isEqualTo("새제목");
            assertThat(show.getVenue()).isEqualTo("새공연장");
            assertThat(show.getShowDate()).isEqualTo(newDate);
        }

        @Test
        @DisplayName("title만 null이면 title만 기존 값 유지, 나머지는 갱신된다")
        void keepsOnlyTitleWhenTitleIsNull() {
            Show show = newShow();
            LocalDateTime newDate = LocalDateTime.of(2027, 1, 1, 20, 0);

            show.update(null, "새공연장", newDate);

            assertThat(show.getTitle()).isEqualTo("원제목");   // 그대로
            assertThat(show.getVenue()).isEqualTo("새공연장");
            assertThat(show.getShowDate()).isEqualTo(newDate);
        }

        @Test
        @DisplayName("전부 null이면 아무것도 안 바뀐다 (PATCH 의미론)")
        void keepsAllFieldsWhenAllNull() {
            Show show = newShow();
            String originalTitle = show.getTitle();
            String originalVenue = show.getVenue();
            LocalDateTime originalDate = show.getShowDate();

            show.update(null, null, null);

            assertThat(show.getTitle()).isEqualTo(originalTitle);
            assertThat(show.getVenue()).isEqualTo(originalVenue);
            assertThat(show.getShowDate()).isEqualTo(originalDate);
        }

        @Test
        @DisplayName("seatGrades는 update()의 대상이 아니라 그대로 유지된다")
        void seatGradesAreNeverTouchedByUpdate() {
            Show show = newShow();
            List<SeatGrade> originalGrades = show.getSeatGrades();

            show.update("새제목", "새공연장", LocalDateTime.now());

            assertThat(show.getSeatGrades()).isEqualTo(originalGrades);
        }
    }
}