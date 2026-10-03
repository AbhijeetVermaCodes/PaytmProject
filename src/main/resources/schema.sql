-- Schema DDL for High-Concurrency Ticket Booking System
-- Database: PostgreSQL 14+

CREATE TABLE IF NOT EXISTS shows (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price_paise BIGINT NOT NULL,
    per_user_limit INT NOT NULL DEFAULT 4,
    total_seats INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS seats (
    id VARCHAR(36) PRIMARY KEY,
    show_id VARCHAR(36) NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    held_until TIMESTAMP WITH TIME ZONE,
    reservation_id VARCHAR(36),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_show_seat_number UNIQUE (show_id, seat_number)
);

CREATE INDEX IF NOT EXISTS idx_seats_show_status ON seats(show_id, status);
CREATE INDEX IF NOT EXISTS idx_seats_show_seat_num ON seats(show_id, seat_number);

CREATE TABLE IF NOT EXISTS reservations (
    id VARCHAR(36) PRIMARY KEY,
    show_id VARCHAR(36) NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    user_id VARCHAR(100) NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    idempotency_key VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reservations_user_show ON reservations(user_id, show_id, status);
CREATE INDEX IF NOT EXISTS idx_reservations_show_id ON reservations(show_id);
CREATE INDEX IF NOT EXISTS idx_reservations_idempotency ON reservations(idempotency_key);

CREATE TABLE IF NOT EXISTS reservation_seats (
    reservation_id VARCHAR(36) NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    seat_number VARCHAR(20) NOT NULL,
    PRIMARY KEY (reservation_id, seat_number)
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_code INT,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_idempotency_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_user_key ON idempotency_records(user_id, idempotency_key);
