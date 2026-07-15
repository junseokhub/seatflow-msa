package com.seatflow.show.integration;

import com.seatflow.common.event.show.SeatGradeType;
import com.seatflow.common.test.composition.MongoContainerSupport;
import com.seatflow.show.domain.Outbox;
import com.seatflow.show.domain.SeatGrade;
import com.seatflow.show.domain.Show;
import com.seatflow.show.repository.OutboxRepository;
import com.seatflow.show.repository.ShowRepository;
import com.seatflow.show.service.ShowService;
import com.seatflow.show.service.command.CreateShowCommand;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ShowService.createShow()가 Show 저장 + Outbox 적재를 하나의 MongoDB 트랜잭션 (replica set 필요)으로 원자적으로 커밋하는지,
 * 그리고 생성된 id가 Outbox의 messageKey에 정확히 반영되는지(save() 반환값 버그를 실제로 겪었던 지점)를 진짜 MongoDB 위에서 검증한다.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShowCreationIntegrationTest implements MongoContainerSupport {

    @Autowired
    private ShowService showService;
    @Autowired
    private ShowRepository showRepository;
    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanUp() {
        showRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("공연 생성 시 Show와 Outbox가 같은 트랜잭션으로 함께 저장된다")
    void createShowSavesShowAndOutboxTogether() {
        CreateShowCommand command = new CreateShowCommand(
                "제목", "공연장", LocalDateTime.of(2026, 12, 25, 19, 0),
                List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));

        Show created = showService.createShow(command);

        assertThat(created.getId()).isNotBlank();
        assertThat(showRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("Outbox에 적재된 messageKey는 생성된 Show의 실제 id와 정확히 일치한다 (save 반환값 버그 회귀 방지)")
    void outboxMessageKeyMatchesActualGeneratedShowId() {
        CreateShowCommand command = new CreateShowCommand(
                "제목", "공연장", LocalDateTime.of(2026, 12, 25, 19, 0),
                List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));

        Show created = showService.createShow(command);

        List<Outbox> outboxes = outboxRepository.findAll();
        assertThat(outboxes).hasSize(1);
        assertThat(outboxes.get(0).getMessageKey()).isEqualTo(created.getId());
        assertThat(outboxes.get(0).getMessageKey()).isNotBlank();   // null이나 빈 문자열이 아님을 명확히
    }

    @Test
    @DisplayName("Outbox의 payload는 유효한 JSON이고 eventVersion을 포함한다")
    void outboxPayloadContainsEventVersion() {
        CreateShowCommand command = new CreateShowCommand(
                "제목", "공연장", LocalDateTime.of(2026, 12, 25, 19, 0),
                List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));

        showService.createShow(command);

        Outbox outbox = outboxRepository.findAll().get(0);
        assertThat(outbox.getPayload()).contains("eventVersion").contains("aggregateId");
    }

    @Test
    @DisplayName("여러 공연을 연속 생성하면 각각 독립적인 Outbox 항목이 정확히 생긴다")
    void multipleShowCreationsProduceIndependentOutboxEntries() {
        CreateShowCommand command1 = new CreateShowCommand(
                "공연1", "공연장1", LocalDateTime.of(2026, 12, 25, 19, 0),
                List.of(new SeatGrade(SeatGradeType.VIP, 10, BigDecimal.valueOf(100000))));
        CreateShowCommand command2 = new CreateShowCommand(
                "공연2", "공연장2", LocalDateTime.of(2026, 12, 26, 19, 0),
                List.of(new SeatGrade(SeatGradeType.R, 20, BigDecimal.valueOf(50000))));

        Show show1 = showService.createShow(command1);
        Show show2 = showService.createShow(command2);

        List<Outbox> outboxes = outboxRepository.findAll();
        assertThat(outboxes).hasSize(2);
        assertThat(outboxes).extracting(Outbox::getMessageKey)
                .containsExactlyInAnyOrder(show1.getId(), show2.getId());
    }
}