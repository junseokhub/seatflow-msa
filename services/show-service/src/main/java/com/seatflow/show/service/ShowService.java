package com.seatflow.show.service;

import com.seatflow.show.domain.Show;
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
        return showRepository.findById(id).orElse(null);
    }
}
