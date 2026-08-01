# 数据库迁移

Flyway 是唯一 schema authority。migration 名称使用递增版本 `VNNNN__short_description.sql`（必要时为 Java migration）；版本由产生表/约束的功能模块维护，跨模块 schema 由 bootstrap 明确协调。已在任何共享环境执行的版本永不重写、删除或重排。

迁移是前向的：发现缺陷时新增修复 migration，先在空库和已升级库验证，再记录影响和恢复步骤。若校验和变化，停止发布，恢复已提交原文件；只有经过审计的环境重建才能 repair，绝不能用 repair 掩盖已发布版本差异。

数据回填采用稳定主键或时间窗口分批：限定 batch size、短事务、可重跑过滤条件、进度指标和暂停开关。先写/验证新列，再回填并查询剩余 NULL/重复/孤儿记录，最后的非空或唯一约束只能在剩余数为零时加入。

每个 migration 审查锁范围、索引构建方式、长事务风险和语句超时；大表变更优先在线/分阶段方案。回退使用补偿 migration 或兼容代码，不对 Flyway history 反向编辑。生产发布后至少执行：

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) AS remaining_nulls FROM target_table WHERE target_column IS NULL;
SELECT constraint_name FROM information_schema.table_constraints WHERE table_name = 'target_table';
```

将真实表/列替换进发布 runbook，并保留查询输出、耗时、锁等待和 backfill 批次计数作为回退证据。
