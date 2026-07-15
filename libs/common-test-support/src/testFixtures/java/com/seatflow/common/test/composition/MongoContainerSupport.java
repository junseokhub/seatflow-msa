package com.seatflow.common.test.composition;

import org.testcontainers.containers.MongoDBContainer;

public interface MongoContainerSupport {

    MongoDBContainer MONGO = new MongoDBContainer("mongo:latest");

    static void startContainer() {
        if (!MONGO.isRunning()) {
            MONGO.start();
        }
    }
}