package com.seatflow.show.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.show.SeatGradeType;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.show.domain.Outbox;
import com.seatflow.show.domain.SeatGrade;
import com.seatflow.show.domain.Show;
import com.seatflow.show.exception.ShowErrorCode;
import com.seatflow.show.repository.OutboxRepository;
import com.seatflow.show.repository.ShowRepository;
import com.seatflow.show.service.command.CreateShowCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {

    @Mock
    private ShowRepository showRepository;
    @Mock
    private OutboxRepository outboxRepository;

    private ShowService showService;

    @BeforeEach
    void setUp() {
        ObjectMapper realObjectMapper = new ObjectMapper().findAndRegisterModules();
        showService = new ShowService(showRepository, outboxRepository, realObjectMapper);
    }

    private Show show(String id) {
        return Show.builder()
                .id(id).title("제목").venue("공연장")
                .showDate(LocalDateTime.of(2026, 12, 25, 19, 0))
                .seatGrades(List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createShow()")
    class CreateShow {

        @Test
        @DisplayName("Show를 저장하고 show.created 이벤트를 Outbox에 함께 적재한다")
        void savesShowAndAppendsOutbox() {
            CreateShowCommand command = new CreateShowCommand(
                    "제목", "공연장", LocalDateTime.of(2026, 12, 25, 19, 0),
                    List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));
            given(showRepository.save(any(Show.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            Show result = showService.createShow(command);

            assertThat(result.getTitle()).isEqualTo("제목");
            verify(showRepository).save(any(Show.class));
            verify(outboxRepository).save(any(Outbox.class));
        }

        @Test
        @DisplayName("toJson 실패 시 예외가 발생하여 트랜잭션이 롤백된다")
        void shouldThrowExceptionWhenSerializationFails() throws Exception {
            CreateShowCommand command = new CreateShowCommand(
                    "제목", "공연장", LocalDateTime.of(2026, 12, 25, 19, 0),
                    List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));

            given(showRepository.save(any(Show.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            ObjectMapper spyObjectMapper = spy(new ObjectMapper().findAndRegisterModules());
            showService = new ShowService(showRepository, outboxRepository, spyObjectMapper);

            given(spyObjectMapper.writeValueAsString(any()))
                    .willThrow(new JsonProcessingException("Serialization failed") {});
            assertThatThrownBy(() -> showService.createShow(command))
                    .isInstanceOf(BusinessException.class);

            verify(outboxRepository, never()).save(any(Outbox.class));
        }

        @Test
        @DisplayName("Outbox에 적재되는 이벤트의 messageKey는 생성된 show의 id다")
        void outboxMessageKeyMatchesShowId() {
            CreateShowCommand command = new CreateShowCommand(
                    "제목", "공연장", LocalDateTime.of(2026, 12, 25, 19, 0),
                    List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));
            given(showRepository.save(any(Show.class))).willAnswer(invocation -> {
                Show s = invocation.getArgument(0);
                return show("generated-id-123");   // 저장 시 id가 생성됐다고 가정
            });

            ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

            showService.createShow(command);

            verify(outboxRepository).save(outboxCaptor.capture());
            assertThat(outboxCaptor.getValue().getMessageKey()).isEqualTo("generated-id-123");
            assertThat(outboxCaptor.getValue().getEventType()).isNotBlank();
        }

        @Test
        @DisplayName("여러 등급이 정확히 이벤트 payload에 담긴다")
        void multipleGradesAreIncludedInPayload() {
            CreateShowCommand command = new CreateShowCommand(
                    "제목", "공연장", LocalDateTime.of(2026, 12, 25, 19, 0),
                    List.of(
                            new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000)),
                            new SeatGrade(SeatGradeType.R, 20, BigDecimal.valueOf(50000))
                    ));
            given(showRepository.save(any(Show.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

            showService.createShow(command);

            verify(outboxRepository).save(outboxCaptor.capture());
            String payload = outboxCaptor.getValue().getPayload();
            assertThat(payload).contains("VIP").contains("R");
        }
    }

    @Nested
    @DisplayName("getShow()")
    class GetShow {

        @Test
        @DisplayName("존재하는 공연을 반환한다")
        void returnsExistingShow() {
            given(showRepository.findById("show-1")).willReturn(Optional.of(show("show-1")));

            Show result = showService.getShow("show-1");

            assertThat(result.getId()).isEqualTo("show-1");
        }

        @Test
        @DisplayName("존재하지 않으면 SHOW_NOT_FOUND 예외를 던진다")
        void throwsWhenNotFound() {
            given(showRepository.findById("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> showService.getShow("unknown"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ShowErrorCode.SHOW_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("getShows()")
    class GetShows {

        @Test
        @DisplayName("전체 공연 목록을 반환한다")
        void returnsAllShows() {
            given(showRepository.findAll()).willReturn(List.of(show("show-1"), show("show-2")));

            List<Show> result = showService.getShows();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("updateShow()")
    class UpdateShow {

        @Test
        @DisplayName("존재하는 공연을 수정하고 저장한다")
        void updatesAndSavesShow() {
            Show existing = show("show-1");
            given(showRepository.findById("show-1")).willReturn(Optional.of(existing));
            given(showRepository.save(any(Show.class))).willAnswer(invocation -> invocation.getArgument(0));

            Show result = showService.updateShow("show-1", "새제목", null, null);

            assertThat(result.getTitle()).isEqualTo("새제목");
            verify(showRepository).save(existing);
        }

        @Test
        @DisplayName("존재하지 않는 공연 수정 시 SHOW_NOT_FOUND 예외를 던진다")
        void throwsWhenUpdatingNonExistentShow() {
            given(showRepository.findById("unknown")).willReturn(Optional.empty());

            assertThatThrownBy(() -> showService.updateShow("unknown", "제목", null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ShowErrorCode.SHOW_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("deleteShow()")
    class DeleteShow {

        @Test
        @DisplayName("존재하는 공연을 삭제한다")
        void deletesExistingShow() {
            given(showRepository.existsById("show-1")).willReturn(true);

            showService.deleteShow("show-1");

            verify(showRepository).deleteById("show-1");
        }

        @Test
        @DisplayName("존재하지 않는 공연 삭제 시 SHOW_NOT_FOUND 예외를 던지고 삭제를 시도하지 않는다")
        void throwsWhenDeletingNonExistentShow() {
            given(showRepository.existsById("unknown")).willReturn(false);

            assertThatThrownBy(() -> showService.deleteShow("unknown"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ShowErrorCode.SHOW_NOT_FOUND.getMessage());

            verify(showRepository, never()).deleteById(any());
        }
    }
}