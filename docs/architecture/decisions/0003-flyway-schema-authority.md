# ADR 0003：Flyway 是 schema authority

- 状态：已接受
- 日期：2026-08-01

## 决策

所有 schema 和数据演进经版本化 Flyway migrations 交付；JPA 使用 `ddl-auto=validate`，SQL initialization 关闭。dev seed 仅在 dev profile 的可重复 location 中存在，不能成为测试或生产 schema 来源。

## 理由与后果

前向 migration、校验和和空库/升级库测试让环境收敛并保留审计轨迹。失败的 migration 通过新的补偿版本修复；禁止编辑已发布文件、手工 schema 漂移或以 repair 隐藏校验和差异。
