package com.seatflow.seat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
		"com.seatflow.seat.domain",
		"com.seatflow.common.outbox.jpa"
})
@EnableJpaRepositories(basePackages = {
		"com.seatflow.seat.repository",
		"com.seatflow.common.outbox.jpa"
})
public class SeatServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SeatServiceApplication.class, args);
	}

}