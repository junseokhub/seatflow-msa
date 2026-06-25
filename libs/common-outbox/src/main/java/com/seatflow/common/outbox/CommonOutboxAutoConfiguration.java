package com.seatflow.common.outbox;

import com.seatflow.common.kafka.config.CommonKafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration(after = CommonKafkaAutoConfiguration.class)
@Import({OutboxScheduler.class})
public class CommonOutboxAutoConfiguration {
}