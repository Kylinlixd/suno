CREATE TABLE IF NOT EXISTS suno_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    parent_id BIGINT NULL,
    icon_url VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS suno_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sn_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    production_date DATE NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    wear_score INT NOT NULL,
    recycle_grade VARCHAR(32) NOT NULL,
    estimated_recycle_price DECIMAL(12, 2) NOT NULL,
    CONSTRAINT uk_product_sn_code UNIQUE (sn_code)
);

CREATE TABLE IF NOT EXISTS suno_product_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    image_type VARCHAR(16) NOT NULL DEFAULT 'PRODUCT',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES suno_product(id)
);

CREATE TABLE IF NOT EXISTS suno_valuation_rule (
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

CREATE TABLE IF NOT EXISTS suno_recycle_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    estimated_price DECIMAL(12, 2) NOT NULL,
    grade VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_recycle_order_order_no UNIQUE (order_no),
    CONSTRAINT fk_recycle_order_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id),
    CONSTRAINT fk_recycle_order_product FOREIGN KEY (product_id) REFERENCES suno_product(id)
);

CREATE TABLE IF NOT EXISTS suno_logistics_track (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tracking_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_logistics_track_tracking_no UNIQUE (tracking_no),
    CONSTRAINT fk_logistics_order FOREIGN KEY (order_id) REFERENCES suno_recycle_order(id)
);

CREATE TABLE IF NOT EXISTS suno_points_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    points_delta INT NOT NULL,
    reason VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_points_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id)
);
