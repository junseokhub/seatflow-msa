CREATE TABLE processed_event (
     consumer_group VARCHAR(100) NOT NULL,
     event_id       VARCHAR(36)  NOT NULL,
     event_type     VARCHAR(100) NOT NULL,
     processed_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
     PRIMARY KEY (consumer_group, event_id)
);