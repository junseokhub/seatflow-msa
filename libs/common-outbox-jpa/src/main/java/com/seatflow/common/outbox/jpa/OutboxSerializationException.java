package com.seatflow.common.outbox.jpa;

/**
 * Outbox 페이로드 직렬화 실패. 이벤트를 JSON으로 만들지 못하면 비즈니스 트랜잭션을
 * 진행할 수 없으므로(이벤트 유실 방지) 언체크 예외로 던져 롤백을 유도한다.
 */
public class OutboxSerializationException extends RuntimeException {
    public OutboxSerializationException(Throwable cause) {
        super("Failed to serialize outbox payload", cause);
    }
}