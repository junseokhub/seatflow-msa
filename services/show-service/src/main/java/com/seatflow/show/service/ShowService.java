package com.seatflow.show.service;

import com.seatflow.common.exception.BusinessException;
import com.seatflow.show.domain.Show;
import com.seatflow.show.exception.ShowErrorCode;
import com.seatflow.show.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
