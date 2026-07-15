package com.seatflow.common.test.composition;

// Redis는 공식 Testcontainers 모듈이 없어 GenericContainer를 쓴다. GenericContainer는
// 타입만으로 "이게 Redis다"를 Spring이 확신할 수 없어서, @ServiceConnection의 name
// 속성으로 명시한다("redis:7-alpine"처럼 이미지 이름에 "redis"가 포함되면 자동
// 인식되긴 하지만, 명시적으로 쓰는 게 더 안전하다 — Spring 공식 문서 예제도 이 방식).

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public interface RedisContainerSupport {

    @Container
    @ServiceConnection(name = "redis")
    GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static void registerDefaultProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}