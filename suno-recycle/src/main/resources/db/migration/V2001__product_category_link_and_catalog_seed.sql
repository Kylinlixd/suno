-- M1 商品目录能力：激活 suno_category，建立 product -> category 关联。
-- 分类种子使用固定 ID，便于环境间一致；已存在同名分类时不重复插入。
INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 1, '手机', NULL, NULL, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 1);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 2, '电脑', NULL, NULL, 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 2);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 3, '平板', NULL, NULL, 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 3);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 4, '影音数码', NULL, NULL, 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 4);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 5, '智能穿戴', NULL, NULL, 50, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 5);

INSERT INTO suno_category (id, name, parent_id, icon_url, sort_order, status, created_at, updated_at)
SELECT 6, '数码配件', NULL, NULL, 60, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM suno_category WHERE id = 6);

ALTER TABLE suno_product ADD COLUMN category_id BIGINT NULL;

ALTER TABLE suno_product
    ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES suno_category (id);

CREATE INDEX idx_product_category_id ON suno_product (category_id);
