-- P1 交易增强：积分抵现、发货物流单号、优惠券到期提醒去重。
ALTER TABLE suno_resale_order ADD COLUMN points_used INT NULL;
ALTER TABLE suno_resale_order ADD COLUMN points_deduct_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE suno_resale_order ADD COLUMN tracking_no VARCHAR(64) NULL;

ALTER TABLE suno_coupon_user ADD COLUMN expire_reminded_at TIMESTAMP NULL;
