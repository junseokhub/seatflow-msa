package com.seatflow.common.outbox;

import java.util.List;

/**
 * Outbox 저장소 동작 계약(포트). 구현(어댑터)은 각 서비스가 자기 DB로 제공한다.
 *   - JPA  : SELECT ... FOR UPDATE SKIP LOCKED 기반
 *   - Mongo: findAndModify 기반
 * 공통 스케줄러(OutboxScheduler)는 이 인터페이스에만 의존하므로 특정 DB를 모른다.
 * common-outbox 모듈에는 JPA/Mongo 의존성이 전혀 들어가지 않는다.
 */
public interface OutboxStore {

    /**
     * PENDING을 limit개까지 원자적으로 집어 PUBLISHING으로 전이시켜 반환.
     * 반환 원소 타입은 구현의 구체 엔티티(OutboxMessage의 하위)이며, 호출 측(스케줄러)은
     * OutboxMessage로만 다룬다. 와일드카드로 두어 구현이 List<Outbox> 등 자기 타입을
     * 변환 복사 없이 그대로 반환할 수 있게 한다.
     */
    List<? extends OutboxMessage> claimPending(int limit);

    void markPublished(String id);

    void markFailedOrRetry(String id);

    void recoverStuck(int minutes);

    long redriveFailed(int limit);
}