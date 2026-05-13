-- ============================
-- 用户账户
-- ============================
INSERT INTO user_account (id, username, password_hash, role_code, account_status, level, points)
VALUES (1001, 'alice', '{noop}user123', 'USER', 'ACTIVE', 'VIP', 1200),
       (1002, 'bob', '{noop}user123', 'USER', 'ACTIVE', 'NORMAL', 300),
       (9001, 'admin', '{noop}admin123', 'ADMIN', 'ACTIVE', 'ADMIN', 99999);

-- ============================
-- 估值规则
-- ============================
INSERT INTO valuation_rule (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
VALUES ('DEMO_BRAND', 'DEMO_MODEL', 0, 18, 80, 100, 'GOOD', 2200.00),
       ('DEMO_BRAND', 'DEMO_MODEL', 19, 36, 60, 100, 'MEDIUM', 1500.00),
       ('DEMO_BRAND', 'DEMO_MODEL', 0, 60, 0, 59, 'UNQUALIFIED', 500.00),
       ('ALL', 'ALL', 0, 18, 0, 100, 'GOOD', 1800.00),
       ('ALL', 'ALL', 19, 36, 0, 100, 'MEDIUM', 1200.00),
       ('ALL', 'ALL', 37, 240, 0, 100, 'UNQUALIFIED', 300.00);

-- ============================
-- 商品
-- ============================
INSERT INTO product (id, sn_code, brand, model, production_date, image_url, wear_score, recycle_grade, estimated_recycle_price)
VALUES (1, 'SN-DEMO-001', 'DEMO_BRAND', 'DEMO_MODEL', '2024-01-15', '/images/demo-001.jpg', 90, 'GOOD', 2200.00),
       (2, 'SN-DEMO-002', 'DEMO_BRAND', 'DEMO_MODEL', '2023-06-10', '/images/demo-002.jpg', 75, 'MEDIUM', 1500.00),
       (3, 'SN-DEMO-003', 'DEMO_BRAND', 'DEMO_MODEL', '2022-03-20', '/images/demo-003.jpg', 40, 'UNQUALIFIED', 500.00);

-- ============================
-- 回收订单
-- ============================
INSERT INTO recycle_order (id, order_no, user_id, product_id, estimated_price, grade, status, created_at)
VALUES (1, 'RC-20250101-001', 1001, 1, 2200.00, 'GOOD', 'COMPLETED', '2025-01-01 10:00:00'),
       (2, 'RC-20250102-002', 1002, 2, 1500.00, 'MEDIUM', 'COMPLETED', '2025-01-02 14:30:00'),
       (3, 'RC-20250103-003', 1001, 3, 500.00, 'UNQUALIFIED', 'PENDING', '2025-01-03 09:00:00');

-- ============================
-- 物流跟踪
-- ============================
INSERT INTO logistics_track (id, tracking_no, order_id, status, updated_at)
VALUES (1, 'SF1234567890', 1, 'DELIVERED', '2025-01-03 16:00:00'),
       (2, 'SF0987654321', 2, 'IN_TRANSIT', '2025-01-04 10:00:00');

-- ============================
-- 积分流水
-- ============================
INSERT INTO points_ledger (id, user_id, points_delta, reason, created_at)
VALUES (1, 1001, 2200, 'RECYCLE_REWARD', '2025-01-01 10:00:00'),
       (2, 1002, 1500, 'RECYCLE_REWARD', '2025-01-02 14:30:00'),
       (3, 1001, -500, 'REDEEM_COUPON', '2025-01-05 11:00:00');

-- ============================
-- 二手商品上架
-- ============================
INSERT INTO resale_listing (id, recycle_order_id, product_id, sale_price, stock, status, created_at, version)
VALUES (1, 1, 1, 2500.00, 1, 'ON_SALE', '2025-01-05 10:00:00', 0),
       (2, 2, 2, 1800.00, 1, 'ON_SALE', '2025-01-06 09:00:00', 0);

-- ============================
-- 二手商品收藏
-- ============================
INSERT INTO resale_favorite (id, user_id, listing_id, created_at)
VALUES (1, 1002, 1, '2025-01-06 11:00:00');

-- ============================
-- 二手商品订单
-- ============================
INSERT INTO resale_order (id, order_no, buyer_user_id, listing_id, amount, pay_status, fulfill_status, created_at)
VALUES (1, 'RS-20250107-001', 1002, 1, 2500.00, 'PAID', 'FULFILLED', '2025-01-07 10:00:00');

-- ============================
-- 二手商品评价
-- ============================
INSERT INTO resale_review (id, order_id, listing_id, user_id, rating, content, image_urls, append_content, merchant_reply, sensitive_hit, moderation_status, moderated_at, created_at, appended_at, replied_at)
VALUES (1, 1, 1, 1002, 5, '商品质量很好，非常满意！', '/images/review-1.jpg', NULL, '感谢您的好评！', 0, 'NORMAL', '2025-01-08 09:00:00', '2025-01-07 15:00:00', NULL, '2025-01-08 10:00:00');

-- ============================
-- 评价投票
-- ============================
INSERT INTO resale_review_vote (id, review_id, user_id, created_at)
VALUES (1, 1, 1001, '2025-01-08 12:00:00');

-- ============================
-- 操作审计日志
-- ============================
INSERT INTO operation_audit_log (id, action_type, target_type, target_id, detail, created_at)
VALUES (1, 'CREATE', 'RECYCLE_ORDER', '1', '用户alice创建回收订单RC-20250101-001', '2025-01-01 10:00:00'),
       (2, 'UPDATE', 'RECYCLE_ORDER', '2', '回收订单RC-20250102-002状态变更为COMPLETED', '2025-01-02 14:30:00');
