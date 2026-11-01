-- R1 回收购物车表 + 扩展分类（图书/衣物/家居/美妆）。
-- 回收车与购物车同构：用户暂存待回收旧物，可连续添加，一次性提交为多笔回收单。
CREATE TABLE IF NOT EXISTS suno_recycle_cart (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    barcode VARCHAR(64) NULL,
    title VARCHAR(256) NOT NULL,
    author VARCHAR(256) NULL,
    publisher VARCHAR(256) NULL,
    publish_year INT NULL,
    category VARCHAR(64) NULL,
    wear_score INT NOT NULL,
    estimated_price DECIMAL(12, 2) NOT NULL,
    grade VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'SCAN',
    status VARCHAR(16) NOT NULL DEFAULT 'IN_CART',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_recycle_cart_user FOREIGN KEY (user_id) REFERENCES suno_user_account (id)
);

CREATE INDEX idx_recycle_cart_user ON suno_recycle_cart (user_id);
CREATE INDEX idx_recycle_cart_status ON suno_recycle_cart (status);

-- 扩展分类：覆盖二手循环常见品类
INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 7, '图书', NULL, NULL, 70, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 7);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 8, '衣物', NULL, NULL, 80, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 8);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 9, '家居', NULL, NULL, 90, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 9);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 10, '美妆', NULL, NULL, 100, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 10);
