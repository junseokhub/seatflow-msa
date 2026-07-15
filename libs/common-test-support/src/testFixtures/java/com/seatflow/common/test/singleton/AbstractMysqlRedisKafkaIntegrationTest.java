package com.seatflow.common.test.singleton;

/**
 * MySQL + Redis + Kafka가 다 필요한 서비스(seat-service, payment-service, auth-service)가 상속한다.
 * Singleton Container Pattern
 * static 블록에서 JVM당 한 번만 세 컨테이너를 띄운다.
 *
 * Redis는 운영에서 Cluster(3노드)지만 여기서는 단일 인스턴스로 띄운다.
 * common-redis의 RedisConfig가 "test" 프로필에서 standalone으로 연결하도록 분기돼 있어(RedisConfig의 @Profile("test") 빈),
 * 이 컨테이너의 host/port를 그대로 흘려보내면 된다.
 * 애플리케이션 코드는 안 건드린다.
 *
 * Kafka는 운영에서 Strimzi(KRaft, broker/controller 분리)지만, 테스트에서는 단일 인스턴스 KRaft로 충분하다.
 * 검증 대상은 클러스터 구성 자체가 아니라 내가 만든 프로듀서/컨슈머 코드가 맞게 동작하는지이기 때문이다.
 */

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractMysqlRedisKafkaIntegrationTest {

    protected static final MySQLContainer<?> MYSQL;
    protected static final GenericContainer<?> REDIS;
    protected static final ConfluentKafkaContainer KAFKA;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("seatflow_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        MYSQL.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);
        REDIS.start();

        KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0")
                .withReuse(true);
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}