package com.seatflow.common.event;

/**
 * Outbox로 발행되는 이벤트가 자신의 스키마 버전을 스스로 알려주기 위한 계약.
 * EventEnvelope.eventVersion 필드를 채우는 데 쓰인다 — payload의 구조가
 * 나중에 바뀌더라도, 컨슈머가 이 버전 값을 보고 어떤 형태의 payload인지
 * 구분할 수 있게 하기 위함이다.
 *
 * Outbox에 적재되는(OutboxAppender.append로 전달되는) 모든 이벤트 record는
 * 이 인터페이스를 구현해야 한다.
 */
public interface VersionedEvent {
    String eventVersion();
}