CREATE TABLE users (
   id         VARCHAR(36)     NOT NULL,
   email      VARCHAR(255)    NOT NULL,
   name       VARCHAR(100)    NOT NULL,
   phone      VARCHAR(20),
   status     VARCHAR(20)     NOT NULL,
   created_at DATETIME(6)     NOT NULL,
   updated_at DATETIME(6)     NOT NULL,
   PRIMARY KEY (id),
   UNIQUE KEY uk_users_email (email)
);