package com.seatflow.common.redis.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "spring.data.redis")
public record RedisProperties(
        String password,
        Cluster cluster
) {
    public record Cluster(
            List<String> nodes
    ) {}
}