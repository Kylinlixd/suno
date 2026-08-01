# ADR 0004：数据库 outbox 与作业

- 状态：已接受
- 日期：2026-08-01

## 决策

Feature 模块拥有公开领域事实和聚合状态；它们在同一事务中调用 `suno-core` 的 `EventOutbox` port。Operations 拥有该 port 的数据库 outbox 存储、后台领取/投递和重试记录，bootstrap 将实现接入 feature 模块。HTTP 请求只创建或查询任务，不同步执行可重试的导出、发布或长时工作。消费者使用稳定 eventId 幂等。

## 理由与后果

该模式避免“状态已提交但事件丢失”及请求超时重试造成的重复副作用。它引入 outbox 表、可观测性、领取锁、退避、死信/重放和保留策略；Operations 维护物理机制，事件拥有模块维护事件契约，任何 controller 都不能直接发布。
