package com.seatflow.common.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}