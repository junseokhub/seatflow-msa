package com.seatflow.common.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@Import({OutboxJpaConfiguration.class, OutboxStore.class, OutboxPublisher.class})
public class CommonOutboxAutoConfiguration {
}