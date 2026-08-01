# 可执行仓库红色基线

于 `2026-07-29T14:56:16+0800` 在分支
`codex/professional-rearchitecture` 采集。本基线刻意保持红色，用于 Phase 0
模块化改造。不得将这些失败视为通过，也不得削弱仓库校验脚本以隐藏它们。

## 工具链

```text
Apache Maven 3.9.15 (98b2cdbfdb5f1ac8781f537ea9acccaed7922349)
Maven home: /Users/leexd/.maven/maven-3.9.15
Java version: 25.0.3, vendor: Microsoft, runtime: /Library/Java/JavaVirtualMachines/microsoft-25.jdk/Contents/Home
Default locale: zh_CN_#Hans, platform encoding: UTF-8
OS name: "mac os x", version: "26.5.2", arch: "aarch64", family: "mac"

openjdk version "25.0.3" 2026-04-21 LTS
OpenJDK Runtime Environment Microsoft-13877172 (build 25.0.3+9-LTS)
OpenJDK 64-Bit Server VM Microsoft-13877172 (build 25.0.3+9-LTS, mixed mode, sharing)
```

## `mvn test` 结果

退出码：`1`（编译失败）。测试数量：`0`；Maven 在测试阶段开始前的编译阶段停止。

```text
[INFO] --- compiler:3.14.0:compile (default-compile) @ suno-mall ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 135 source files with javac [debug parameters release 25] to target/classes
[INFO] -------------------------------------------------------------
[WARNING] COMPILATION WARNING :
[INFO] -------------------------------------------------------------
[WARNING] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/SecurityConfig.java:[91,46] org.springframework.security.authentication.dao.DaoAuthenticationProvider中的DaoAuthenticationProvider()已过时
[WARNING] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/SecurityConfig.java:[92,17] org.springframework.security.authentication.dao.DaoAuthenticationProvider中的setUserDetailsService(org.springframework.security.core.userdetails.UserDetailsService)已过时
[INFO] 2 warnings
[INFO] -------------------------------------------------------------
[INFO] -------------------------------------------------------------
[ERROR] COMPILATION ERROR :
[INFO] -------------------------------------------------------------
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[19,8] com.suno.mall.config.TransactionConfig不是抽象的, 并且未覆盖org.springframework.transaction.annotation.TransactionManagementConfigurer中的抽象方法annotationDrivenTransactionManager()
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[24,5] 方法不会覆盖或实现超类型的方法
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/dao/RecycleRepositoryTransactional.java:[29,52] 不兼容的类型: java.util.Optional<com.suno.mall.entity.RecycleOrderEntity>无法转换为com.suno.mall.entity.RecycleOrderEntity
[INFO] 3 errors
[INFO] -------------------------------------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.720 s
[INFO] Finished at: 2026-07-29T14:56:23+08:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile (default-compile) on project suno-mall: Compilation failure: Compilation failure:
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[19,8] com.suno.mall.config.TransactionConfig不是抽象的, 并且未覆盖org.springframework.transaction.annotation.TransactionManagementConfigurer中的抽象方法annotationDrivenTransactionManager()
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/config/TransactionConfig.java:[24,5] 方法不会覆盖或实现超类型的方法
[ERROR] /Users/leexd/Documents/Codex/2026-07-29/new-chat/work/suno/src/main/java/com/suno/mall/dao/RecycleRepositoryTransactional.java:[29,52] 不兼容的类型: java.util.Optional<com.suno.mall.entity.RecycleOrderEntity>无法转换为com.suno.mall.entity.RecycleOrderEntity
[ERROR] -> [Help 1]
```

## 仓库校验结果

在清理已有的受跟踪编译产物、不安全配置默认值和过时 README 脚本引用之前，
`./scripts/verify-repository.sh` 应当失败。脚本会在一次运行中报告当前每类违规。

采集时间：`2026-07-29T14:57:31+0800`；退出码：`1`。

```text
src/main/java/com/suno/mall/dto/response/RecycleOrderVO.class
Tracked build artifact detected
src/main/resources/application-mysql.yml:51:    secret: demo-payment-secret
src/main/resources/application.yml:63:    secret: demo-payment-secret
Unsafe configuration default detected
README references missing local script: ./scripts/run-tests-ci.sh
README references missing local script: ./scripts/run-tests.sh
```

## Phase 0 收口验证

于 `2026-08-01` 在分支 `codex/professional-rearchitecture` 执行。下列结果是
本次收口的实际证据；它们不表示后续领域阶段的安全或业务缺陷已经修复。

### 前后对比

| 项目 | Task 1 红色基线 | Phase 0 收口证据 |
|---|---|---|
| 构建结构 | 单模块，主源码编译失败，0 个测试执行 | 9 个 reactor 项目（parent + 8 个模块）；`./mvnw -DskipITs verify` 成功 |
| 单元/架构/文档 | 未能开始测试 | 最终 `-DskipITs verify` 执行 67 个测试，0 failure、0 error、0 skipped；Checkstyle、Enforcer dependency convergence、duplicate-class 与 JaCoCo 已配置门禁均通过 |
| 文档覆盖 | 无可执行目录校验 | `./scripts/verify-docs.sh` 于 `2026-08-01T16:03:26+08:00` 成功；目录/流程覆盖测试 4 个，0 failure、0 error、0 skipped，未完成内容扫描通过 |
| 数据库 | JPA `suno_*` 映射与旧 SQL 不一致，默认启动不可靠 | 空 H2 已验证并迁移 7 个版本化 migration 至 `v5900`；dev 启动还应用 repeatable dev seed，共 8 个已应用 migration。隔离本地 MySQL 也已由 Flyway 建立并通过 JPA 校验（7 个版本化 migration、28 张 canonical 表） |
| 仓库卫生 | 受跟踪 `.class`、已知 demo secret、README 缺失脚本 | `./scripts/verify-repository.sh` 成功；tracked artifact、已知默认 secret 与 README 可执行脚本扫描均无违规 |
| 生产 profile | staging/prod 可继承 H2 与 mock provider 默认值 | 独立复审发现后已修复：staging/prod 必须提供 `SUNO_DB_URL`、数据库凭据及 real provider endpoint/credential；`FlywayLocationProfileTest` 4/4 通过 |

### Maven、Flyway 与 Docker 证据

- `./mvnw -DskipITs verify`：于 `2026-08-01T17:07:05+08:00` 退出码 `0`，67 个测试均通过；Failsafe integration-test/verify 阶段按该显式本地 flag 跳过。H2 Flyway 测试验证空库 7 个版本化 migration 和 dev seed replay；覆盖门禁通过。
- `./mvnw verify`：退出码 `0`，总计 `39.572 s`。它不是 MySQL 通过的证据：Failsafe 执行 12 个 integration tests，其中 5 个 skipped（`FlywayMySqlIT` 2、`SchemaInvariantIT` 1、`LegacySchemaCompatibilityIT` 2）。
- Docker 客户端可用，但 daemon 不可用，原始环境证据为：`Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?` 因此本次没有 Docker-backed MySQL migration 或 schema-invariant 成功结论；Docker 恢复后必须重新执行 `./mvnw verify`。
- root Enforcer 在本次每次 Maven reactor 执行中均报告 `DependencyConvergence passed`；`git diff --check`、`git status --short`、受跟踪 binary scan 与具体已知 secret scan 均已复核。Flyway H2 validation 在测试与真实 dev 进程中均成功。

### 隔离本地 MySQL 启动证据

于 `2026-08-01`，使用 `mysql` profile 在仅用于本次验证的隔离本地 MySQL
数据库 `suno_phase0_verify` 上完成启动验证。该记录不包含 URL、用户名、密码或其他
连接凭据。

- 应用成功启动；Flyway 校验并应用 7 个版本化 migration，规范 schema 包含 28 张 canonical 表。
- Hibernate/JPA `ddl-auto=validate` 通过；`TINYINT(1)` 布尔映射及
  `suno_payment_replay_auto_handle_idempotency.response_json` 的 `TEXT` 映射均已随 MySQL 启动路径验证。
- 匿名 `GET /actuator/health/liveness` 和 `GET /actuator/health/readiness` 均返回 `{"status":"UP"}`。
- 进程正常完成 graceful shutdown。

这是本地 MySQL 实例的运行时证据，不替代 Docker/Testcontainers 的
`FlywayMySqlIT`、`SchemaInvariantIT` 或 `LegacySchemaCompatibilityIT`；它们在本机
Docker daemon 不可用时仍为 skipped，Docker 恢复后仍须执行 `./mvnw verify`。

### 真实 dev/H2 启动证据

以新内存库 `jdbc:h2:mem:phase0_final_20260801`、`dev` profile 和临时外部 secret 启动已打包 JAR。应用在 `2026-08-01T16:05:39+08:00` 于端口 `18081` 启动；Flyway 校验 8 个 migration 并应用至 `v5900`。

- 匿名 `GET /actuator/health/liveness`：`{"status":"UP"}`。
- 匿名 `GET /actuator/health/readiness`：`{"status":"UP"}`。
- 以 dev seed 的 ADMIN 登录后，readiness 为 `UP`，其中 `db=UP`（H2），`flywayReadiness=UP`，`validationSuccessful=true`，`appliedMigrations=8`，`readinessState=UP`。
- 进程接收 `SIGINT` 后记录 `Graceful shutdown complete`，随后关闭 JPA 和 Hikari datasource。

### 独立复审与剩余风险

独立复审覆盖构建可重复性、Flyway/schema、文档覆盖与意外行为变化。其一个高优先级发现（staging/prod 继承 H2/mock 默认值）已由 profile 显式外部 MySQL 与 real-provider 设置、以及 4 个 profile 配置测试关闭。两个中优先级发现也已关闭：Docker MySQL 8.4 测试镜像固定为 OCI index digest `sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`，而 callback-secret 子进程 smoke test 清除所有继承的 `SPRING_*` 输入。仍需在后续工作处理：

- Docker daemon 不可用使 Docker-backed `FlywayMySqlIT`、legacy compatibility 与 schema-invariant 路径尚未得到本机实证；隔离本地 MySQL 启动已验证 Flyway、Hibernate 映射和健康探针，但不取代这些 Testcontainers 集成测试。
- Phase 1 仍须解决身份与会话的业务安全缺陷，尤其 refresh replay 分支在回滚事务中撤销/审计不持久化、access token 立即失效语义，以及独立安全事件/outbox 边界；Phase 0 仅记录并守护当前行为。

### 后续计划入口

- [Phase 1 Identity requirements](../requirements/identity.md)：认证、refresh/session、RBAC 与安全事件。
- [Phase 2 Payment requirements](../requirements/payment.md)：回调验签、幂等账本、重放与任务领取。
- [Phase 3 Marketplace requirements](../requirements/marketplace.md)：CurrentActor、资源所有权、库存/订单/评价状态机。
- [Phase 4 Recycle requirements](../requirements/recycle.md)：服务端估值、积分、外部调用和短事务。
- [Phase 5 Operations requirements](../requirements/operations.md)：审计、配置、导出和运维任务。
- [Approved target design](../superpowers/specs/2026-07-29-suno-platform-rearchitecture-design.md)：阶段目标、强制不变量与迁移策略。
