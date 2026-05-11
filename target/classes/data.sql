INSERT INTO user_account (id, username, password_hash, role_code, account_status, level, points)
VALUES (1001, 'alice', '{noop}user123', 'USER', 'ACTIVE', 'VIP', 1200),
       (1002, 'bob', '{noop}user123', 'USER', 'ACTIVE', 'NORMAL', 300),
       (9001, 'admin', '{noop}admin123', 'ADMIN', 'ACTIVE', 'ADMIN', 99999);

INSERT INTO valuation_rule (brand, model, min_months, max_months, min_wear_score, max_wear_score, grade, price)
VALUES ('DEMO_BRAND', 'DEMO_MODEL', 0, 18, 80, 100, 'GOOD', 2200.00),
       ('DEMO_BRAND', 'DEMO_MODEL', 19, 36, 60, 100, 'MEDIUM', 1500.00),
       ('DEMO_BRAND', 'DEMO_MODEL', 0, 60, 0, 59, 'UNQUALIFIED', 500.00),
       ('ALL', 'ALL', 0, 18, 0, 100, 'GOOD', 1800.00),
       ('ALL', 'ALL', 19, 36, 0, 100, 'MEDIUM', 1200.00),
       ('ALL', 'ALL', 37, 240, 0, 100, 'UNQUALIFIED', 300.00);
