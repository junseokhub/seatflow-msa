package com.seatflow.common.test.composition;

// 인터페이스 컴포지션 방식. Spring Boot 공식 문서(Testcontainers :: Spring Boot)가
// 소개하는 패턴 — 컨테이너를 인터페이스의 static 필드로 선언하고, 테스트 클래스가
// 필요한 인터페이스들을 implements로 자유롭게 섞어 쓴다. Java가 다중 상속을 지원 안
// 하는 것과 달리 다중 인터페이스 구현은 되므로, 조합(MySQL+Redis+Kafka 등)이
// 늘어나도 클래스를 새로 만들 필요가 없다.
//
// @ServiceConnection을 같이 써서, 기존 Abstract*IntegrationTest 계열이 수동으로
// 하던 @DynamicPropertySource 매핑(url/username/password를 한 줄씩 등록)을
// 아예 없앤다 — Spring Boot가 MySQLContainer 타입을 인식해서 자동으로 datasource
// 커넥션 정보를 연결해준다.

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

public interface MysqlContainerSupport {

    @Container
    @ServiceConnection
    MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("seatflow_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
}