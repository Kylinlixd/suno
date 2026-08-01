# ADR 0002：模块化单体

- 状态：已接受
- 日期：2026-08-01

## 决策

保持一个可部署的 Spring Boot 进程，但以 Identity、Recycle、Marketplace、Payment、Operations 和 Core Maven 模块建立编译期边界。bootstrap 作为唯一 composition root；模块间只能经公开 `api` port 或已登记事件通信。

## 理由与后果

这在不引入分布式调用失败面的前提下，先把所有权、测试和迁移边界变为可执行规则。代价是仍共享运行时和数据库，因此所有跨模块动作必须保持短事务，不能把模块目录误认为服务隔离。未来独立部署只能从稳定 API/outbox 边界开始，而不是复制 controller 或 repository。
