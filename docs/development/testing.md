# 测试策略

| 层级 | 责任 | 命名/夹具 |
| --- | --- | --- |
| Unit | 聚合、值对象、纯策略和错误分支 | `*Test`；夹具由测试类或所属模块 builder 拥有 |
| Application | 用例事务、端口协调、幂等和审计 | `*Test`；替身只位于该模块测试源集 |
| Web | 路由、认证、参数和稳定响应 | `*WebTest`；请求夹具在 controller 所属模块 |
| Persistence | JPA 映射、Flyway、查询和约束 | `*Test`/`*IT`；schema 夹具由 persistence 拥有 |
| Concurrency | 乐观锁、条件更新、重放 | `*ConcurrencyTest`；每个测试独立数据 |
| Provider | 外部签名、回调、时间/网络失败 | `*ProviderTest`；不调用生产外部系统 |
| E2E | 关键跨模块路径和发布后验证 | `*IT`；只使用可回收环境 |
| Architecture | 模块、层、事件和文档契约 | `*BoundaryTest`、`Documentation*Test` |

测试方法名描述行为和结果，例如 `rejectsReplayWhenNonceWasConsumed`；不可用编号或含混名称。共享 fixture 必须由创建其业务语义的模块拥有，调用方只通过公开 builder/测试支持库使用；不得让生产 seed 充当测试 fixture。

本地快速命令：

```bash
./mvnw -pl suno-bootstrap -am test
./mvnw -pl suno-bootstrap -am -Dtest=DocumentationCatalogCoverageTest,DocumentationFlowCoverageTest -Dsurefire.failIfNoSpecifiedTests=false test
./scripts/verify-requirement-flows.sh --task 13
./scripts/verify-docs.sh
```

CI/发布门禁：

```bash
./mvnw verify
./scripts/verify-repository.sh
./scripts/verify-docs.sh
```

Docker 可用时 `verify` 同时运行 MySQL integration tests；Docker 不可用时不得把跳过的 MySQL 结果当作生产数据库证据。
