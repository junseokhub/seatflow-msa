package com.seatflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.seatflow")
@EntityScan(basePackages = {
		"com.seatflow.payment.domain",
		"com.seatflow.common.outbox.jpa"
})
@EnableJpaRepositories(basePackages = {
		"com.seatflow.payment.repository",
		"com.seatflow.common.outbox.jpa"
})
@EnableFeignClients(basePackages = "com.seatflow.common.client")
@EnableScheduling
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}