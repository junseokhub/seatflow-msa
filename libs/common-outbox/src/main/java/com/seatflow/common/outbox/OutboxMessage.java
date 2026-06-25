package com.seatflow.common.outbox;

/**
 * Outbox 메시지가 발행 시점에 노출해야 하는 최소 계약.
 * 공통 스케줄러는 구체 엔티티(JPA @Entity / Mongo @Document)를 모르고
 * 이 인터페이스로만 메시지를 다룬다.
 *
 * id는 String으로 통일한다. JPA(Long PK)는 toString으로, Mongo(String PK)는 그대로 노출.
 */
public interface OutboxMessage {
    String getId();
    String getEventType();
    String getMessageKey();
    String getPayload();
    String getEventId();
}