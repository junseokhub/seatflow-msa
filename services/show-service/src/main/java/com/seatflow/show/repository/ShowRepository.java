package com.seatflow.show.repository;

import com.seatflow.show.domain.Show;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShowRepository extends MongoRepository<Show, String> {
}
