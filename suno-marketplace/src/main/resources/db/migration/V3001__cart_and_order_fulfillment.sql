-- M2 交易配套：购物车表 + 订单履约/优惠列。
-- 二手单件模型：购物车数量恒为 1，唯一约束防止重复加购。
CREATE TABLE IF NOT EXISTS suno_cart_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_cart_user_listing UNIQUE (user_id, listing_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES suno_user_account (id),
    CONSTRAINT fk_cart_listing FOREIGN KEY (listing_id) REFERENCES suno_resale_listing (id)
);

CREATE INDEX idx_cart_user_id ON suno_cart_item (user_id);

-- 订单收货人快照（地址修改/删除不影响已下订单）+ 优惠券核销 + 应付金额
ALTER TABLE suno_resale_order ADD COLUMN receiver_name VARCHAR(64) NULL;
ALTER TABLE suno_resale_order ADD COLUMN receiver_phone VARCHAR(32) NULL;
ALTER TABLE suno_resale_order ADD COLUMN receiver_address VARCHAR(320) NULL;
ALTER TABLE suno_resale_order ADD COLUMN coupon_user_id BIGINT NULL;
ALTER TABLE suno_resale_order ADD COLUMN discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE suno_resale_order ADD COLUMN payable_amount DECIMAL(12, 2) NULL;

ALTER TABLE suno_resale_order
    ADD CONSTRAINT fk_resale_order_coupon_user FOREIGN KEY (coupon_user_id) REFERENCES suno_coupon_user (id);
