package com.seatflow.common.test.composition;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.ConfluentKafkaContainer;

public interface KafkaContainerSupport {

    @Container
    @ServiceConnection
    ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0")
                    .withReuse(true);
}