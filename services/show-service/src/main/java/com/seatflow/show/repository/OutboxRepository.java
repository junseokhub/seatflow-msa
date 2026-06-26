package com.seatflow.show.repository;

import com.seatflow.show.domain.Outbox;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Mongo Outbox 저장용 repository.
 * 적재(save)에 사용. PENDING 집기/상태전이 같은 동시성 제어는 MongoOutboxStore가
 * MongoTemplate.findAndModify로 직접 수행하므로, 여기는 기본 저장/조회만 제공한다.
 */
public interface OutboxRepository extends MongoRepository<Outbox, String> {
}
