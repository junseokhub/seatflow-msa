package com.seatflow.common.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.kafka")
public record KafkaBootstrapProperties(
        String bootstrapServers
) {}