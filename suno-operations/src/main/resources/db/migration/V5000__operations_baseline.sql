CREATE TABLE IF NOT EXISTS suno_operation_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    detail VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS suno_auth_export_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    export_type VARCHAR(32) NOT NULL,
    export_format VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 2,
    file_name VARCHAR(128) NULL,
    content_text VARCHAR(255) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NULL,
    CONSTRAINT uk_auth_export_task_task_id UNIQUE (task_id)
);

CREATE TABLE IF NOT EXISTS suno_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(1024) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    related_id BIGINT NULL,
    related_type VARCHAR(32) NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);
