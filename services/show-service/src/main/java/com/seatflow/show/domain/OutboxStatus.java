package com.seatflow.show.domain;

public enum OutboxStatus {
    PENDING,      // 발행 대기
    PUBLISHING,   // 발행 중 (집힌 상태)
    PUBLISHED,    // 발행 완료
    FAILED        // 재시도 초과 ->격리 (수동 redrive)
}
