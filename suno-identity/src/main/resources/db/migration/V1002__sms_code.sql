-- P1 短信验证码：手机号登录/注册的一次性验证码。
-- 发送通道由 SmsProvider 接口隔离（mock 默认，生产接短信服务商）。
CREATE TABLE IF NOT EXISTS suno_sms_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(32) NOT NULL,
    code VARCHAR(8) NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT idx_sms_code_phone UNIQUE (phone, id)
);

CREATE INDEX idx_sms_code_phone_expire ON suno_sms_code (phone, expire_at);
