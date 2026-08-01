CREATE TABLE IF NOT EXISTS suno_payment_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    pay_status_snapshot VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS suno_payment_replay_auto_handle_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    response_json TEXT NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_payment_replay_auto_trace UNIQUE (trace_id)
);

CREATE TABLE IF NOT EXISTS suno_payment_nonce (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nonce VARCHAR(128) NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_payment_nonce_nonce UNIQUE (nonce)
);

CREATE TABLE IF NOT EXISTS suno_payment_callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    pay_status VARCHAR(32) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    timestamp BIGINT NOT NULL,
    signature VARCHAR(128) NOT NULL,
    callback_status VARCHAR(32) NOT NULL,
    error_message VARCHAR(512) NULL,
    response_body VARCHAR(255) NOT NULL,
    source VARCHAR(32) NOT NULL,
    replay_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_replay_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS suno_payment_replay_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    callback_log_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL,
    last_error VARCHAR(512) NULL,
    next_retry_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL
);
