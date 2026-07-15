package com.seatflow.common.test.composition;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * bootstrap-servers는 "선택 가능한 기본값"이 아니라 이 컨테이너를 쓰는 이상 항상 필요한 필수 연결 정보다.
 * 그래서 registerDefaultProperties처럼 호출은 선택인 형태 대신, 인터페이스를 구현하는 즉시 자동 적용되는
 * @DynamicPropertySource로 인터페이스 자체에 선언한다.
 * 이러면 이 인터페이스를 implements하는 순간 컴포지션이 원래 의도한 대로 이 능력을 온전히 갖는것이 성립한다.
 * MySQL의 ddl-auto/flyway처럼 진짜 다르게 설정하고 싶을 수 있는 값만 별도의 선택적 static 메서드로 남겨둔다.
 */
public interface KafkaContainerSupport {

    ConfluentKafkaContainer KAFKA = createAndStart();

    private static ConfluentKafkaContainer createAndStart() {
        ConfluentKafkaContainer container =
                new ConfluentKafkaContainer("confluentinc/cp-kafka:7.6.0");
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}