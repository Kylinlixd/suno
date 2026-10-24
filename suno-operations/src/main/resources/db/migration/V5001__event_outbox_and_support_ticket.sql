-- P3 事件外发箱：领域事实先落库再异步派发（至少一次投递语义，消费方幂等）。
CREATE TABLE IF NOT EXISTS suno_event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    use_case_id VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    payload VARCHAR(2048) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL
);

CREATE INDEX idx_event_outbox_status_id ON suno_event_outbox (status, id);

-- P2 客服工单：买家提交，运营回复并推进状态。
CREATE TABLE IF NOT EXISTS suno_support_ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(64) NULL,
    ticket_type VARCHAR(32) NOT NULL,
    content VARCHAR(1024) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    reply VARCHAR(1024) NULL,
    replied_by VARCHAR(64) NULL,
    replied_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT uk_support_ticket_no UNIQUE (ticket_no)
);

CREATE INDEX idx_support_ticket_user_id ON suno_support_ticket (user_id);
CREATE INDEX idx_support_ticket_status ON suno_support_ticket (status);
