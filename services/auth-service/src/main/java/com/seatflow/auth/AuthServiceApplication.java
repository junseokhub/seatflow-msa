package com.seatflow.auth;

import com.seatflow.auth.config.properties.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.seatflow")
@EntityScan(basePackages = {
		"com.seatflow.auth.domain",
		"com.seatflow.common.outbox.jpa"
})
@EnableJpaRepositories(basePackages = {
		"com.seatflow.auth.repository",
		"com.seatflow.common.outbox.jpa"
})
@EnableConfigurationProperties(JwtProperties.class)
@EnableScheduling
public class AuthServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}
}