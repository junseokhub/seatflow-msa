package com.seatflow.common.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({OutboxStore.class, OutboxPublisher.class})
public class CommonOutboxAutoConfiguration {
}