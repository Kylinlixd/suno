-- M1 注册能力：suno_user_account.id 为手工主键（基线种子使用固定 id），
-- 自助注册改用 JPA TableGenerator 发号。初始值必须高于已存在的最大 id
-- （dev 种子为 9001），从 10001 开始，避免与种子或手工插入冲突。
CREATE TABLE IF NOT EXISTS suno_user_id_generator (
    generator_key VARCHAR(64) NOT NULL PRIMARY KEY,
    next_val BIGINT NOT NULL
);

INSERT INTO suno_user_id_generator (generator_key, next_val)
SELECT 'suno_user_account', 10001
WHERE NOT EXISTS (SELECT 1 FROM suno_user_id_generator WHERE generator_key = 'suno_user_account');
