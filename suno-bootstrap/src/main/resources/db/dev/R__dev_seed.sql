INSERT INTO suno_user_account
    (id, username, password_hash, role_code, account_status, level, points)
SELECT 1001, 'alice', '{noop}user123', 'USER', 'ACTIVE', 'VIP', 1200
WHERE NOT EXISTS (SELECT 1 FROM suno_user_account WHERE username = 'alice');

INSERT INTO suno_user_account
    (id, username, password_hash, role_code, account_status, level, points)
SELECT 1002, 'bob', '{noop}user123', 'USER', 'ACTIVE', 'NORMAL', 300
WHERE NOT EXISTS (SELECT 1 FROM suno_user_account WHERE username = 'bob');

INSERT INTO suno_user_account
    (id, username, password_hash, role_code, account_status, level, points)
SELECT 9001, 'admin', '{noop}admin123', 'ADMIN', 'ACTIVE', 'ADMIN', 99999
WHERE NOT EXISTS (SELECT 1 FROM suno_user_account WHERE username = 'admin');

INSERT INTO suno_valuation_rule
    (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
SELECT 'DEMO_BRAND', 'DEMO_MODEL', 0, 18, 80, 100, 'GOOD', 2200.00
WHERE NOT EXISTS (
    SELECT 1 FROM suno_valuation_rule
    WHERE brand = 'DEMO_BRAND' AND model = 'DEMO_MODEL' AND min_months = 0 AND grade = 'GOOD'
);

INSERT INTO suno_valuation_rule
    (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
SELECT 'DEMO_BRAND', 'DEMO_MODEL', 19, 36, 60, 100, 'MEDIUM', 1500.00
WHERE NOT EXISTS (
    SELECT 1 FROM suno_valuation_rule
    WHERE brand = 'DEMO_BRAND' AND model = 'DEMO_MODEL' AND min_months = 19 AND grade = 'MEDIUM'
);

INSERT INTO suno_valuation_rule
    (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
SELECT 'DEMO_BRAND', 'DEMO_MODEL', 0, 60, 0, 59, 'UNQUALIFIED', 500.00
WHERE NOT EXISTS (
    SELECT 1 FROM suno_valuation_rule
    WHERE brand = 'DEMO_BRAND' AND model = 'DEMO_MODEL' AND min_months = 0 AND grade = 'UNQUALIFIED'
);

INSERT INTO suno_valuation_rule
    (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
SELECT 'ALL', 'ALL', 0, 18, 0, 100, 'GOOD', 1800.00
WHERE NOT EXISTS (
    SELECT 1 FROM suno_valuation_rule
    WHERE brand = 'ALL' AND model = 'ALL' AND min_months = 0 AND grade = 'GOOD'
);

INSERT INTO suno_valuation_rule
    (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
SELECT 'ALL', 'ALL', 19, 36, 0, 100, 'MEDIUM', 1200.00
WHERE NOT EXISTS (
    SELECT 1 FROM suno_valuation_rule
    WHERE brand = 'ALL' AND model = 'ALL' AND min_months = 19 AND grade = 'MEDIUM'
);

INSERT INTO suno_valuation_rule
    (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
SELECT 'ALL', 'ALL', 37, 240, 0, 100, 'UNQUALIFIED', 300.00
WHERE NOT EXISTS (
    SELECT 1 FROM suno_valuation_rule
    WHERE brand = 'ALL' AND model = 'ALL' AND min_months = 37 AND grade = 'UNQUALIFIED'
);

INSERT INTO suno_product
    (id, sn_code, brand, model, production_date, image_url, wear_score, recycle_grade,
     estimated_recycle_price)
SELECT 1, 'SN-DEMO-001', 'DEMO_BRAND', 'DEMO_MODEL', '2024-01-15', '/images/demo-001.jpg',
       90, 'GOOD', 2200.00
WHERE NOT EXISTS (SELECT 1 FROM suno_product WHERE sn_code = 'SN-DEMO-001');

INSERT INTO suno_product
    (id, sn_code, brand, model, production_date, image_url, wear_score, recycle_grade,
     estimated_recycle_price)
SELECT 2, 'SN-DEMO-002', 'DEMO_BRAND', 'DEMO_MODEL', '2023-06-10', '/images/demo-002.jpg',
       75, 'MEDIUM', 1500.00
WHERE NOT EXISTS (SELECT 1 FROM suno_product WHERE sn_code = 'SN-DEMO-002');

INSERT INTO suno_product
    (id, sn_code, brand, model, production_date, image_url, wear_score, recycle_grade,
     estimated_recycle_price)
SELECT 3, 'SN-DEMO-003', 'DEMO_BRAND', 'DEMO_MODEL', '2022-03-20', '/images/demo-003.jpg',
       40, 'UNQUALIFIED', 500.00
WHERE NOT EXISTS (SELECT 1 FROM suno_product WHERE sn_code = 'SN-DEMO-003');

INSERT INTO suno_recycle_order
    (id, order_no, user_id, product_id, estimated_price, grade, status, created_at)
SELECT 1, 'RC-20250101-001', 1001, 1, 2200.00, 'GOOD', 'COMPLETED', '2025-01-01 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_recycle_order WHERE order_no = 'RC-20250101-001');

INSERT INTO suno_recycle_order
    (id, order_no, user_id, product_id, estimated_price, grade, status, created_at)
SELECT 2, 'RC-20250102-002', 1002, 2, 1500.00, 'MEDIUM', 'COMPLETED', '2025-01-02 14:30:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_recycle_order WHERE order_no = 'RC-20250102-002');

INSERT INTO suno_recycle_order
    (id, order_no, user_id, product_id, estimated_price, grade, status, created_at)
SELECT 3, 'RC-20250103-003', 1001, 3, 500.00, 'UNQUALIFIED', 'PENDING', '2025-01-03 09:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_recycle_order WHERE order_no = 'RC-20250103-003');

INSERT INTO suno_logistics_track (id, tracking_no, order_id, status, updated_at)
SELECT 1, 'SF1234567890', 1, 'DELIVERED', '2025-01-03 16:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_logistics_track WHERE tracking_no = 'SF1234567890');

INSERT INTO suno_logistics_track (id, tracking_no, order_id, status, updated_at)
SELECT 2, 'SF0987654321', 2, 'IN_TRANSIT', '2025-01-04 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_logistics_track WHERE tracking_no = 'SF0987654321');

INSERT INTO suno_points_ledger (id, user_id, points_delta, reason, created_at)
SELECT 1, 1001, 2200, 'RECYCLE_REWARD', '2025-01-01 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_points_ledger WHERE id = 1);

INSERT INTO suno_points_ledger (id, user_id, points_delta, reason, created_at)
SELECT 2, 1002, 1500, 'RECYCLE_REWARD', '2025-01-02 14:30:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_points_ledger WHERE id = 2);

INSERT INTO suno_points_ledger (id, user_id, points_delta, reason, created_at)
SELECT 3, 1001, -500, 'REDEEM_COUPON', '2025-01-05 11:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_points_ledger WHERE id = 3);

INSERT INTO suno_resale_listing
    (id, recycle_order_id, product_id, sale_price, stock, status, created_at, updated_at, version)
SELECT 1, 1, 1, 2500.00, 1, 'ON_SALE', '2025-01-05 10:00:00', '2025-01-05 10:00:00', 0
WHERE NOT EXISTS (SELECT 1 FROM suno_resale_listing WHERE id = 1);

INSERT INTO suno_resale_listing
    (id, recycle_order_id, product_id, sale_price, stock, status, created_at, updated_at, version)
SELECT 2, 2, 2, 1800.00, 1, 'ON_SALE', '2025-01-06 09:00:00', '2025-01-06 09:00:00', 0
WHERE NOT EXISTS (SELECT 1 FROM suno_resale_listing WHERE id = 2);

INSERT INTO suno_resale_favorite (id, user_id, listing_id, created_at)
SELECT 1, 1002, 1, '2025-01-06 11:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_resale_favorite WHERE user_id = 1002 AND listing_id = 1);

INSERT INTO suno_resale_order
    (id, order_no, buyer_user_id, listing_id, amount, pay_status, fulfill_status, created_at)
SELECT 1, 'RS-20250107-001', 1002, 1, 2500.00, 'PAID', 'FULFILLED', '2025-01-07 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_resale_order WHERE order_no = 'RS-20250107-001');

INSERT INTO suno_resale_review
    (id, order_id, listing_id, user_id, rating, content, image_urls, append_content, merchant_reply,
     sensitive_hit, moderation_status, moderated_at, created_at, appended_at, replied_at)
SELECT 1, 1, 1, 1002, 5, '商品质量很好，非常满意！', '/images/review-1.jpg', NULL,
       '感谢您的好评！', 0, 'NORMAL', '2025-01-08 09:00:00', '2025-01-07 15:00:00',
       NULL, '2025-01-08 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_resale_review WHERE order_id = 1 AND user_id = 1002);

INSERT INTO suno_resale_review_vote (id, review_id, user_id, created_at)
SELECT 1, 1, 1001, '2025-01-08 12:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_resale_review_vote WHERE review_id = 1 AND user_id = 1001);

INSERT INTO suno_operation_audit_log
    (id, action_type, target_type, target_id, detail, created_at)
SELECT 1, 'CREATE', 'RECYCLE_ORDER', '1', '用户alice创建回收订单RC-20250101-001',
       '2025-01-01 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_operation_audit_log WHERE id = 1);

INSERT INTO suno_operation_audit_log
    (id, action_type, target_type, target_id, detail, created_at)
SELECT 2, 'UPDATE', 'RECYCLE_ORDER', '2', '回收订单RC-20250102-002状态变更为COMPLETED',
       '2025-01-02 14:30:00'
WHERE NOT EXISTS (SELECT 1 FROM suno_operation_audit_log WHERE id = 2);
