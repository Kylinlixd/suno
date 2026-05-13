
-- 更新数据库表名和相关引用
-- 1. 重命名表
RENAME TABLE recycle_order TO suno_recycle_order;
RENAME TABLE resale_order TO suno_resale_order;
RENAME TABLE resale_listing TO suno_resale_listing;
RENAME TABLE resale_review TO suno_resale_review;
RENAME TABLE resale_review_report TO suno_resale_review_report;
RENAME TABLE resale_review_vote TO suno_resale_review_vote;
RENAME TABLE resale_favorite TO suno_resale_favorite;
RENAME TABLE product TO suno_product;
RENAME TABLE user_account TO suno_user_account;
RENAME TABLE logistics_track TO suno_logistics_track;
RENAME TABLE operation_audit_log TO suno_operation_audit_log;
RENAME TABLE payment_callback_log TO suno_payment_callback_log;
RENAME TABLE payment_idempotency TO suno_payment_idempotency;
RENAME TABLE payment_nonce TO suno_payment_nonce;
RENAME TABLE payment_replay_task TO suno_payment_replay_task;
RENAME TABLE payment_replay_auto_handle_idempotency TO suno_payment_replay_auto_handle_idempotency;
RENAME TABLE points_ledger TO suno_points_ledger;
RENAME TABLE valuation_rule TO suno_valuation_rule;
RENAME TABLE auth_refresh_token TO suno_auth_refresh_token;
RENAME TABLE auth_token_blacklist TO suno_auth_token_blacklist;
RENAME TABLE auth_export_task TO suno_auth_export_task;
RENAME TABLE category TO suno_category;
RENAME TABLE coupon TO suno_coupon;
RENAME TABLE coupon_user TO suno_coupon_user;
RENAME TABLE notification TO suno_notification;
RENAME TABLE refund_order TO suno_refund_order;
RENAME TABLE user_address TO suno_user_address;
RENAME TABLE product_image TO suno_product_image;

-- 2. 更新表中的外键引用
-- 这里需要根据实际情况更新所有表中的外键引用
-- 例如，如果recycle_order表中有引用其他表的字段，需要更新这些引用

-- 3. 更新序列或其他自增ID生成器（如果使用）
-- 如果使用了序列，需要重命名序列
-- 例如：RENAME TABLE recycle_order_seq TO suno_recycle_order_seq;

-- 4. 更新视图（如果有）
-- 例如：RENAME VIEW recycle_order_view TO suno_recycle_order_view;

-- 5. 更新存储过程和触发器（如果有）
-- 例如：RENAME PROCEDURE recycle_order_update TO suno_recycle_order_update;
