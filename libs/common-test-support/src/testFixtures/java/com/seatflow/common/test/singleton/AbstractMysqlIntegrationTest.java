package com.seatflow.common.test.singleton;

// Singleton Container Pattern. @Container 어노테이션(JUnit5 Testcontainers extension이
// 자동으로 시작/종료를 관리하는 방식) 대신, static 블록에서 JVM당 딱 한 번만 컨테이너를
// 띄운다. 테스트 클래스가 여러 개라도(같은 JVM 프로세스 안에서 도는 한) 컨테이너를
// 재사용해 전체 테스트 스위트 실행 시간을 크게 줄인다. Spring/Testcontainers 공식
// 문서가 권장하는 방식이고, 실무에서 가장 흔히 쓰는 패턴이다.
//
// 컨테이너를 명시적으로 stop()하지 않는다 — JVM이 종료되면서 Ryuk(Testcontainers의
// 정리 컨테이너)가 자동으로 정리한다. 로컬 개발에서는 여기에 추가로
// testcontainers.reuse.enable=true를 걸면, JVM이 여러 번(테스트 재실행마다) 새로
// 뜨더라도 이전에 띄운 컨테이너를 계속 재사용할 수 있다.

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractMysqlIntegrationTest {

    protected static final MySQLContainer<?> MYSQL;

    static {
        MYSQL = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("seatflow_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }
}