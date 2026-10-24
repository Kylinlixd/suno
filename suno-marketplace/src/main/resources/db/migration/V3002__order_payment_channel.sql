-- M3 支付渠道字段：真实网关收单后回填渠道、网关交易号与支付时间，用于对账与退款。
ALTER TABLE suno_resale_order ADD COLUMN pay_channel VARCHAR(32) NULL;
ALTER TABLE suno_resale_order ADD COLUMN trade_no VARCHAR(64) NULL;
ALTER TABLE suno_resale_order ADD COLUMN paid_at TIMESTAMP NULL;
