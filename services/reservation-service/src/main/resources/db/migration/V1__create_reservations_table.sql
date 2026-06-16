CREATE TABLE reservations (
  id                 BIGINT      NOT NULL AUTO_INCREMENT,
  reservation_number VARCHAR(36) NOT NULL UNIQUE,
  user_id            VARCHAR(36) NOT NULL,
  show_id            VARCHAR(36) NOT NULL,
  seat_id            BIGINT      NOT NULL,
  status             VARCHAR(20) NOT NULL,
  created_at         DATETIME(6) NOT NULL,
  updated_at         DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_reservations_user_id (user_id),
  INDEX idx_reservations_show_id (show_id)
);