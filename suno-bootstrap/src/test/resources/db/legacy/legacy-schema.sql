CREATE TABLE user_account (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    level VARCHAR(32) NOT NULL,
    points INT NOT NULL DEFAULT 0
);

CREATE TABLE auth_refresh_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expire_at TIMESTAMP NOT NULL,
    revoked TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    CONSTRAINT fk_auth_refresh_token_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE auth_token_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(64) NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE auth_export_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128),
    export_type VARCHAR(32) NOT NULL,
    export_format VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 2,
    file_name VARCHAR(128),
    content_text TEXT,
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL
);

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sn_code VARCHAR(64) NOT NULL UNIQUE,
    brand VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    production_date DATE NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    wear_score INT NOT NULL,
    recycle_grade VARCHAR(32) NOT NULL,
    estimated_recycle_price DECIMAL(12, 2) NOT NULL
);

CREATE TABLE valuation_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    min_months INT NOT NULL,
    max_months INT NOT NULL,
    min_wear_score INT NOT NULL,
    max_wear_score INT NOT NULL,
    grade VARCHAR(32) NOT NULL,
    price DECIMAL(12, 2) NOT NULL
);

CREATE TABLE recycle_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    estimated_price DECIMAL(12, 2) NOT NULL,
    grade VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_recycle_order_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_recycle_order_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE TABLE logistics_track (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tracking_no VARCHAR(64) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_logistics_order FOREIGN KEY (order_id) REFERENCES recycle_order(id)
);

CREATE TABLE points_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    points_delta INT NOT NULL,
    reason VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_points_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE resale_listing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recycle_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sale_price DECIMAL(12, 2) NOT NULL,
    stock INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_listing_recycle_order FOREIGN KEY (recycle_order_id) REFERENCES recycle_order(id),
    CONSTRAINT fk_listing_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE TABLE resale_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_resale_favorite_user_listing UNIQUE (user_id, listing_id),
    CONSTRAINT fk_resale_favorite_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_resale_favorite_listing FOREIGN KEY (listing_id) REFERENCES resale_listing(id)
);

CREATE TABLE resale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    buyer_user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    pay_status VARCHAR(32) NOT NULL,
    fulfill_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_resale_order_buyer FOREIGN KEY (buyer_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_resale_order_listing FOREIGN KEY (listing_id) REFERENCES resale_listing(id)
);

CREATE TABLE resale_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(512) NOT NULL,
    image_urls VARCHAR(1024),
    append_content VARCHAR(512),
    merchant_reply VARCHAR(512),
    sensitive_hit TINYINT(1) NOT NULL DEFAULT 0,
    moderation_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    moderated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    appended_at TIMESTAMP,
    replied_at TIMESTAMP,
    CONSTRAINT uk_resale_review_order_user UNIQUE (order_id, user_id),
    CONSTRAINT fk_resale_review_order FOREIGN KEY (order_id) REFERENCES resale_order(id),
    CONSTRAINT fk_resale_review_listing FOREIGN KEY (listing_id) REFERENCES resale_listing(id),
    CONSTRAINT fk_resale_review_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE resale_review_vote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_resale_review_vote_review_user UNIQUE (review_id, user_id),
    CONSTRAINT fk_resale_review_vote_review FOREIGN KEY (review_id) REFERENCES resale_review(id),
    CONSTRAINT fk_resale_review_vote_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE resale_review_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    reason VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    process_note VARCHAR(256),
    processed_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uk_resale_review_report_review_user UNIQUE (review_id, reporter_user_id),
    CONSTRAINT fk_resale_review_report_review FOREIGN KEY (review_id) REFERENCES resale_review(id),
    CONSTRAINT fk_resale_review_report_user FOREIGN KEY (reporter_user_id) REFERENCES user_account(id)
);

CREATE TABLE operation_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    detail VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE payment_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    order_no VARCHAR(64) NOT NULL,
    pay_status_snapshot VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE payment_replay_auto_handle_idempotency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL UNIQUE,
    response_json TEXT NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE payment_nonce (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nonce VARCHAR(128) NOT NULL UNIQUE,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE payment_callback_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    pay_status VARCHAR(32) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    timestamp BIGINT NOT NULL,
    signature VARCHAR(128) NOT NULL,
    callback_status VARCHAR(32) NOT NULL,
    error_message VARCHAR(512),
    response_body VARCHAR(255) NOT NULL,
    source VARCHAR(32) NOT NULL,
    replay_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_replay_at TIMESTAMP
);

CREATE TABLE payment_replay_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    callback_log_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL,
    last_error VARCHAR(512),
    next_retry_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_recycle_order_status_created_at ON recycle_order(status, created_at);
CREATE INDEX idx_resale_listing_status_created_at ON resale_listing(status, created_at);

INSERT INTO user_account VALUES
    (1001, 'legacy-user', '{noop}legacy', 'USER', 'ACTIVE', 'NORMAL', 321);
INSERT INTO auth_refresh_token
    (id, user_id, username, device_id, token, expire_at, revoked, created_at, revoked_at)
    VALUES (1, 1001, 'legacy-user', 'legacy-device', 'legacy-refresh', '2030-01-01 00:00:00', 0,
            '2025-01-01 00:00:00', NULL);
INSERT INTO auth_token_blacklist VALUES
    (1, 'legacy-jti', 'legacy-user', '2030-01-01 00:00:00', '2025-01-01 00:00:00');
INSERT INTO auth_export_task
    (id, task_id, idempotency_key, export_type, export_format, status, retry_count, max_retry,
     file_name, content_text, error_code, error_message, created_at, finished_at)
    VALUES (1, 'legacy-export', 'legacy-export-key', 'AUDIT', 'CSV', 'DONE', 0, 2,
            'legacy.csv', 'legacy-content', NULL, NULL, '2025-01-01 00:00:00', '2025-01-01 00:01:00');
INSERT INTO product VALUES
    (1, 'LEGACY-SN', 'LegacyBrand', 'LegacyModel', '2024-01-01', '/legacy.jpg', 88, 'GOOD', 1234.56);
INSERT INTO valuation_rule VALUES
    (1, 'LegacyBrand', 'LegacyModel', 0, 12, 80, 100, 'GOOD', 1234.56);
INSERT INTO recycle_order VALUES
    (1, 'LEGACY-RC-1', 1001, 1, 1234.56, 'GOOD', 'COMPLETED', '2025-01-01 10:00:00');
INSERT INTO logistics_track VALUES
    (1, 'LEGACY-TRACK', 1, 'DELIVERED', '2025-01-02 10:00:00');
INSERT INTO points_ledger VALUES
    (1, 1001, 123, 'LEGACY_REWARD', '2025-01-01 10:00:00');
INSERT INTO resale_listing VALUES
    (1, 1, 1, 1500.00, 1, 'ON_SALE', '2025-01-03 10:00:00', 0);
INSERT INTO resale_favorite VALUES
    (1, 1001, 1, '2025-01-03 11:00:00');
INSERT INTO resale_order VALUES
    (1, 'LEGACY-RS-1', 1001, 1, 1500.00, 'PAID', 'FULFILLED', '2025-01-04 10:00:00');
INSERT INTO resale_review
    (id, order_id, listing_id, user_id, rating, content, image_urls, append_content, merchant_reply,
     sensitive_hit, moderation_status, moderated_at, created_at, appended_at, replied_at)
    VALUES (1, 1, 1, 1001, 5, 'legacy-review', NULL, NULL, NULL, 0, 'NORMAL', NULL,
            '2025-01-05 10:00:00', NULL, NULL);
INSERT INTO resale_review_vote VALUES
    (1, 1, 1001, '2025-01-05 11:00:00');
INSERT INTO resale_review_report
    (id, review_id, reporter_user_id, reason, status, process_note, processed_by, created_at, processed_at)
    VALUES (1, 1, 1001, 'legacy-reason', 'PENDING', NULL, NULL, '2025-01-05 12:00:00', NULL);
INSERT INTO operation_audit_log VALUES
    (1, 'CREATE', 'RECYCLE_ORDER', '1', 'legacy-detail', '2025-01-01 10:00:00');
INSERT INTO payment_idempotency VALUES
    (1, 'legacy-payment-key', 'LEGACY-RS-1', 'PAID', '2025-01-04 10:00:00');
INSERT INTO payment_replay_auto_handle_idempotency VALUES
    (1, 'legacy-trace', '{\"legacy\":true}', '2030-01-01 00:00:00', '2025-01-04 10:00:00');
INSERT INTO payment_nonce VALUES
    (1, 'legacy-nonce', '2030-01-01 00:00:00', '2025-01-04 10:00:00');
INSERT INTO payment_callback_log
    (id, order_no, idempotency_key, pay_status, nonce, timestamp, signature, callback_status,
     error_message, response_body, source, replay_count, created_at, last_replay_at)
    VALUES (1, 'LEGACY-RS-1', 'legacy-callback', 'PAID', 'legacy-nonce', 1735960000,
            'legacy-signature', 'SUCCESS', NULL, 'success', 'LEGACY', 0, '2025-01-04 10:00:00', NULL);
INSERT INTO payment_replay_task
    (id, callback_log_id, status, retry_count, last_error, next_retry_at, created_at, updated_at)
    VALUES (1, 1, 'PENDING', 0, NULL, '2025-01-04 10:05:00', '2025-01-04 10:00:00', NULL);
