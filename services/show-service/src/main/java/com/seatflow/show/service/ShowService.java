package com.seatflow.show.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.common.event.EventEnvelope;
import com.seatflow.common.event.EventTopic;
import com.seatflow.common.event.show.ShowCreatedEvent;
import com.seatflow.common.exception.BusinessException;
import com.seatflow.show.domain.Outbox;
import com.seatflow.show.domain.Show;
import com.seatflow.show.exception.ShowErrorCode;
import com.seatflow.show.repository.OutboxRepository;
import com.seatflow.show.repository.ShowRepository;
import com.seatflow.show.service.command.CreateShowCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowService {

    private static final String SOURCE = "show-service";

    private final ShowRepository showRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper kafkaObjectMapper;

    public List<Show> getShows() {
        return showRepository.findAll();
    }

    public Show getShow(String id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ShowErrorCode.SHOW_NOT_FOUND.getStatus().value(),
                        ShowErrorCode.SHOW_NOT_FOUND.getMessage()
                ));
    }

    /**
     * 공연 생성. Show 저장과 show.created 이벤트의 Outbox 적재를 한 트랜잭션으로 묶는다.
     * (MongoTransactionManager + replica set 필요 — dual-write 방지)
     * seat-service가 이 이벤트를 받아 등급별 좌석을 생성한다.
     */
    @Transactional
    public Show createShow(CreateShowCommand command) {
        Show show = Show.builder()
                .title(command.title())
                .venue(command.venue())
                .showDate(command.showDate())
                .seatGrades(command.seatGrades())
                .createdAt(LocalDateTime.now())
                .build();
        show = showRepository.save(show);

        ShowCreatedEvent event = toEvent(show);

        EventEnvelope<ShowCreatedEvent> envelope = EventEnvelope.of(
                EventTopic.SHOW_CREATED, event.eventVersion(), SOURCE, show.getId(), event);
        outboxRepository.save(Outbox.builder()
                .eventId(envelope.eventId())
                .eventType(EventTopic.SHOW_CREATED)
                .messageKey(show.getId())
                .payload(toJson(envelope))
                .build());

        return show;
    }

    /** 제목·공연장·공연일 수정 (null 필드는 기존 값 유지). seatGrades는 변경 불가. */
    @Transactional
    public Show updateShow(String id, String title, String venue, LocalDateTime showDate) {
        Show show = getShow(id);
        show.update(title, venue, showDate);
        return showRepository.save(show);
    }

    /** 공연 삭제. 좌석은 seat-service가 별도 관리하므로 여기서는 show 문서만 제거한다. */
    @Transactional
    public void deleteShow(String id) {
        if (!showRepository.existsById(id)) {
            throw new BusinessException(
                    ShowErrorCode.SHOW_NOT_FOUND.getStatus().value(),
                    ShowErrorCode.SHOW_NOT_FOUND.getMessage());
        }
        showRepository.deleteById(id);
    }

    private ShowCreatedEvent toEvent(Show show) {
        List<ShowCreatedEvent.GradeSpec> grades = show.getSeatGrades().stream()
                .map(g -> new ShowCreatedEvent.GradeSpec(
                        g.grade(),
                        g.capacity(),
                        g.price()))
                .toList();
        return new ShowCreatedEvent(show.getId(), show.getShowDate(), grades);
    }

    private String toJson(Object o) {
        try {
            return kafkaObjectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException(
                    ShowErrorCode.INTERNAL_ERROR.getStatus().value(),
                    ShowErrorCode.INTERNAL_ERROR.getMessage());
        }
    }
}
