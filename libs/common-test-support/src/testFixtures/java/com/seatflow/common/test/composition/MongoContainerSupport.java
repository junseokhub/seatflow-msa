package com.seatflow.common.test.composition;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;

public interface MongoContainerSupport {

    @Container
    @ServiceConnection
    MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0")
            .withReuse(true);
}