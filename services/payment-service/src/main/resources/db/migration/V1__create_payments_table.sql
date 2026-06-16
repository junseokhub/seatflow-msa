CREATE TABLE payments (
  id             BIGINT         NOT NULL AUTO_INCREMENT,
  payment_method VARCHAR(20)    NOT NULL,
  payment_number VARCHAR(36)    NOT NULL UNIQUE,
  reservation_id BIGINT         NOT NULL,
  user_id        VARCHAR(36)    NOT NULL,
  amount         DECIMAL(10, 2) NOT NULL,
  status         VARCHAR(20)    NOT NULL,
  created_at     DATETIME(6)    NOT NULL,
  updated_at     DATETIME(6)    NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_payments_reservation_id (reservation_id),
  INDEX idx_payments_user_id (user_id)
);