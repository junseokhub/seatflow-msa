package com.seatflow.show.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.show.domain.Show;
import com.seatflow.show.exception.ShowErrorCode;
import com.seatflow.show.repository.ShowRepository;
import com.seatflow.show.service.command.CreateShowCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;

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

    public Show createShow(CreateShowCommand command) {
        Show show = Show.builder()
                .title(command.title())
                .venue(command.venue())
                .showDate(command.showDate())
                .totalSeats(command.totalSeats())
                .price(command.price())
                .createdAt(LocalDateTime.now())
                .build();

        return showRepository.save(show);
    }
}
