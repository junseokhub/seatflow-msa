CREATE TABLE seats (
    id         BIGINT          NOT NULL AUTO_INCREMENT,
    show_id    VARCHAR(36)     NOT NULL,
    section    VARCHAR(50)     NOT NULL,
    seat_row        VARCHAR(10)     NOT NULL,
    number     INT             NOT NULL,
    status     VARCHAR(20)     NOT NULL,
    price      INT             NOT NULL,
    created_at DATETIME(6)     NOT NULL,
    updated_at DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_seats_show_section_row_number (show_id, section, seat_row, number)
);