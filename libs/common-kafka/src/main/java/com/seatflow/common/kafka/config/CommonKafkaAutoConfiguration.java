package com.seatflow.common.kafka.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({KafkaProducerConfig.class, KafkaConsumerConfig.class})
public class CommonKafkaAutoConfiguration {
}