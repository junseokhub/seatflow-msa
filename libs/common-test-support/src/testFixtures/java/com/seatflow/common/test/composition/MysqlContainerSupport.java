package com.seatflow.common.test.composition;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

// 인터페이스 컴포지션 방식은 그대로 유지한다. 다만 @Container + @ServiceConnection (자동 감지)은 걷어냈다.
// 여러 테스트 클래스가 withReuse(true)로 같은 컨테이너를 공유할 때,
// 이 자동 감지 방식이 라이프사이클 관리 주체 충돌이나 연결 정보 갱신 타이밍 문제를 겪을 수 있다는 걸 MongoDB/Kafka에서 실제로 확인했다.
// MySQL/Redis는 오늘 이 문제가 겉으로 드러나지 않았지만, 공유 컨테이너라는 조건 자체는 동일해서 잠재 위험이 있다고 보고 일관되게 통일했다.
// 대신 컨테이너 필드 초기화식 안에서 직접 start()를 호출하는 "Singleton Container패턴"을 쓴다
// 인터페이스의 static 필드는 첫 참조 시점에 정확히 한 번만 초기화되므로, 여러 클래스가 공유해도 컨테이너는 하나만 뜬다.
// 연결 정보는 @DynamicPropertySource로 인터페이스 자체에 등록해, implements하는 순간 자동 적용되게 한다.
// 예전에 별도 static 메서드로 두고 각 테스트가 명시적으로 호출해야 했던 것을 없앴다.
public interface MysqlContainerSupport {

    MySQLContainer<?> MYSQL = createAndStart();

    private static MySQLContainer<?> createAndStart() {
        MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("seatflow_test")
                .withUsername("test")
                .withPassword("test");
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    /**
     * ddl-auto, flyway.enabled는 필수 연결정보가 아니라 테스트마다 다르게
     * 쓰고 싶을 수 있는 "선택 가능한 기본값"이라, 인터페이스가 강제하지 않고
     * 원하는 테스트가 골라서 호출한다.
     */
    static void registerDefaultJpaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }
}