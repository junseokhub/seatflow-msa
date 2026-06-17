package com.seatflow.common.outbox;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.seatflow.common.outbox")
@EnableJpaRepositories(basePackages = "com.seatflow.common.outbox")
public class OutboxJpaConfiguration {
}