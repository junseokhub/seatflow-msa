CREATE TABLE outbox (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_id      VARCHAR(36)  NOT NULL UNIQUE,
    event_type    VARCHAR(100) NOT NULL,
    message_key   VARCHAR(36)  NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    publishing_at DATETIME(6)  NULL,
    published_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_poll (status, id),
    INDEX idx_outbox_created_at (created_at)
);