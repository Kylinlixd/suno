CREATE TABLE IF NOT EXISTS suno_user_account (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    level VARCHAR(32) NOT NULL,
    points INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_account_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS suno_user_address (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    province VARCHAR(32) NOT NULL,
    city VARCHAR(32) NOT NULL,
    district VARCHAR(32) NOT NULL,
    detail_address VARCHAR(256) NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id)
);

CREATE TABLE IF NOT EXISTS suno_auth_refresh_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    token VARCHAR(255) NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    revoked TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    CONSTRAINT uk_auth_refresh_token_token UNIQUE (token),
    CONSTRAINT fk_auth_refresh_token_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id)
);

CREATE TABLE IF NOT EXISTS suno_auth_token_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_auth_token_blacklist_jti UNIQUE (jti)
);
