package com.seatflow.show.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Mongo에서 @Transactional을 활성화하기 위한 트랜잭션 매니저.
 * JPA는 자동 등록되지만 Mongo는 이 빈이 있어야 멀티 도큐먼트 트랜잭션이 동작한다.
 * (replica set 모드 필수 — 단일 노드라도 rs로 초기화돼 있으면 OK)
 *
 * 이 빈이 있어야 createShow에서 shows 저장 + outbox 저장을 한 트랜잭션으로 묶어
 * "둘 다 커밋 또는 둘 다 롤백"이 보장된다 (dual-write 방지).
 */
@Configuration
public class MongoTransactionConfig {

    @Bean
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
