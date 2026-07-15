package com.seatflow.common.test.composition;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

/**
 * 인터페이스의 static 필드는 첫 참조 시점에 정확히 한 번만 초기화되므로,
 * 필드 초기화식 안에서 바로 start()를 호출해도 여러 테스트 클래스가 공유해도 안전하다.
 * 별도의 startContainer() + isRunning() 체크 + 각 테스트의 static 블록에서 명시적 호출, 이 세 단계를 거칠 필요가 없었다.
 */
public interface MongoContainerSupport {

    MongoDBContainer MONGO = createAndStart();

    private static MongoDBContainer createAndStart() {
        MongoDBContainer container = new MongoDBContainer("mongo:7.0");
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }
}