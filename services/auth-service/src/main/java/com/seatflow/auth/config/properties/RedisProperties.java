package com.seatflow.auth.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisProperties {

    private String password;
    private Cluster cluster = new Cluster();

    @Getter
    @Setter
    public static class Cluster {
        private List<String> nodes;
    }
}