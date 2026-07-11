package com.seatflow.common.test.singleton;

// MySQL + Kafka만 필요한 서비스(reservation-service, user-service)가 상속한다.
// reservation-service는 여기에 더해 Saga 통합 테스트(17편)에서 더 세밀한 흐름
// 검증이 필요할 수 있는데, 그건 이 클래스를 상속한 뒤 테스트 코드에서 실제
// 이벤트를 발행/구독하는 식으로 다룬다 — 베이스 클래스 자체는 인프라 기동까지만
// 책임진다.

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractMysqlKafkaIntegrationTest {

    protected static final MySQLContainer<?> MYSQL;
    protected static final ConfluentKafkaContainer KAFKA;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("seatflow_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        MYSQL.start();

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

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}