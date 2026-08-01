CREATE TABLE IF NOT EXISTS suno_resale_listing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recycle_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sale_price DECIMAL(12, 2) NOT NULL,
    stock INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_resale_listing_recycle_order UNIQUE (recycle_order_id),
    CONSTRAINT fk_listing_recycle_order FOREIGN KEY (recycle_order_id) REFERENCES suno_recycle_order(id),
    CONSTRAINT fk_listing_product FOREIGN KEY (product_id) REFERENCES suno_product(id)
);

CREATE TABLE IF NOT EXISTS suno_resale_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_resale_favorite_user_listing UNIQUE (user_id, listing_id),
    CONSTRAINT fk_resale_favorite_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id),
    CONSTRAINT fk_resale_favorite_listing FOREIGN KEY (listing_id) REFERENCES suno_resale_listing(id)
);

CREATE TABLE IF NOT EXISTS suno_resale_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    pay_status VARCHAR(32) NOT NULL,
    fulfill_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_resale_order_order_no UNIQUE (order_no),
    CONSTRAINT fk_resale_order_buyer FOREIGN KEY (buyer_user_id) REFERENCES suno_user_account(id),
    CONSTRAINT fk_resale_order_listing FOREIGN KEY (listing_id) REFERENCES suno_resale_listing(id)
);

CREATE TABLE IF NOT EXISTS suno_resale_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(512) NOT NULL,
    image_urls VARCHAR(1024) NULL,
    append_content VARCHAR(512) NULL,
    merchant_reply VARCHAR(512) NULL,
    sensitive_hit TINYINT(1) NOT NULL DEFAULT 0,
    moderation_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    moderated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    appended_at TIMESTAMP NULL,
    replied_at TIMESTAMP NULL,
    CONSTRAINT uk_resale_review_order_user UNIQUE (order_id, user_id),
    CONSTRAINT fk_resale_review_order FOREIGN KEY (order_id) REFERENCES suno_resale_order(id),
    CONSTRAINT fk_resale_review_listing FOREIGN KEY (listing_id) REFERENCES suno_resale_listing(id),
    CONSTRAINT fk_resale_review_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id)
);

CREATE TABLE IF NOT EXISTS suno_resale_review_vote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_resale_review_vote_review_user UNIQUE (review_id, user_id),
    CONSTRAINT fk_resale_review_vote_review FOREIGN KEY (review_id) REFERENCES suno_resale_review(id),
    CONSTRAINT fk_resale_review_vote_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id)
);

CREATE TABLE IF NOT EXISTS suno_resale_review_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    reason VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    process_note VARCHAR(256) NULL,
    processed_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NULL,
    CONSTRAINT uk_resale_review_report_review_user UNIQUE (review_id, reporter_user_id),
    CONSTRAINT fk_resale_review_report_review FOREIGN KEY (review_id) REFERENCES suno_resale_review(id),
    CONSTRAINT fk_resale_review_report_user FOREIGN KEY (reporter_user_id) REFERENCES suno_user_account(id)
);

CREATE TABLE IF NOT EXISTS suno_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    coupon_type VARCHAR(16) NOT NULL DEFAULT 'FIXED',
    discount_value DECIMAL(12, 2) NOT NULL,
    min_order_amount DECIMAL(12, 2) NULL,
    total_count INT NOT NULL,
    remaining_count INT NOT NULL,
    per_user_limit INT NOT NULL DEFAULT 1,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS suno_coupon_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    used_at TIMESTAMP NULL,
    order_id BIGINT NULL,
    expire_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_coupon_user_user FOREIGN KEY (user_id) REFERENCES suno_user_account(id),
    CONSTRAINT fk_coupon_user_coupon FOREIGN KEY (coupon_id) REFERENCES suno_coupon(id)
);

CREATE TABLE IF NOT EXISTS suno_refund_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL,
    resale_order_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    reason VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    refund_channel VARCHAR(32) NULL,
    refund_transaction_no VARCHAR(64) NULL,
    admin_remark VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NULL,
    CONSTRAINT uk_refund_order_refund_no UNIQUE (refund_no)
);
