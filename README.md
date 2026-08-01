# Suno Mall

> 面向二手商品循环交易的 C2B2C 模块化单体平台

[![Verify](https://github.com/Kylinlixd/suno/actions/workflows/verify.yml/badge.svg)](https://github.com/Kylinlixd/suno/actions/workflows/verify.yml)

Suno Mall 将“回收估价 → 物流履约 → 二次上架 → C 端购买 → 支付与售后”串成一条可审计的业务链路。项目当前以 **Phase 0 专业化基线**为主，重点验证模块边界、数据库迁移、认证安全、幂等处理、运行探针和可重复交付能力。

这不是一个分布式微服务集合，而是一个可演进的 **模块化单体（modular monolith）**：业务边界先在同一运行时内通过 Maven 模块、应用用例、端口和事件契约隔离，待边界稳定后再评估独立部署。

## 先看这里

| 目标 | 入口 |
| --- | --- |
| 了解模块边界与架构决策 | [架构说明](docs/architecture/modules.md) |
| 了解业务流程与需求覆盖 | [业务流程图](docs/business-flow.md)、[需求目录](docs/requirements/README.md) |
| 本地启动、Profile、密钥与探针 | [配置与运维](docs/development/configuration.md) |
| Flyway、Schema 与迁移策略 | [数据库迁移](docs/development/migrations.md) |
| 测试分层与质量门禁 | [测试策略](docs/development/testing.md) |
| 分支、提交、审查与交付 | [开发工作流](docs/development/workflow.md) |
| 全量文档索引 | [docs/README.md](docs/README.md) |

## 业务闭环

```text
用户提交回收申请
        │
        ▼
图片审核 / SN 解析 / 服务端估价
        │
        ▼
回收单审核 ──► 物流追踪 ──► 积分流水
        │
        ▼
二次商品发布 ──► 下单 ──► 支付回调与幂等
                              │
                              ▼
                       发货 / 收货 / 退款
                              │
                              ▼
                    评价、举报、审计与运营分析
```

核心能力按领域分组如下：

| 领域 | 主要能力 |
| --- | --- |
| Identity | JWT、RBAC、Refresh Token 轮换、多设备会话、重放防护 |
| Recycle | 回收单、图片审核、SN 解析、估价、物流和积分 |
| Marketplace | 商品列表、订单、库存、收藏、评价、举报与履约 |
| Payment | 回调验签、幂等账本、重放队列、死信再投递和运维处置 |
| Operations | 配置中心、审计日志、安全事件、导出任务和告警降噪 |

## 架构与模块边界

仓库由 **9 个 Maven Reactor 项目**组成：父工程加 8 个模块。`suno-bootstrap` 负责 Spring Boot 运行时和当前 HTTP/JPA 适配器；其余模块承载逐步迁移中的内核、领域契约、应用用例和测试支持。模块化单体阶段不通过网络调用模块，而通过显式依赖、端口和事件契约协作。

| 模块 | 职责 | 边界约束 |
| --- | --- | --- |
| `suno-core` | 共享内核、领域事件、事件版本和 Use Case 标识 | 不依赖 Web、JPA 或具体外部平台 |
| `suno-identity` | 身份与会话领域的迁移边界 | 通过公开契约与 Bootstrap 协作 |
| `suno-recycle` | 回收领域的迁移边界 | 估价、外部调用和事务边界逐步收敛 |
| `suno-marketplace` | 二销商城领域的迁移边界 | 商品、订单、库存与评价保持业务内聚 |
| `suno-payment` | 支付领域的迁移边界 | 回调、幂等和重放不跨越领域泄漏状态 |
| `suno-operations` | 运维用例、事件和升级协作契约 | 审计与配置能力通过事件/端口接入 |
| `suno-test-support` | Testcontainers、共享测试支持和夹具边界 | 仅测试作用域，不反向污染生产模块 |
| `suno-bootstrap` | 启动入口、HTTP 适配器、JPA、Flyway、Provider 和定时任务 | 组合模块，不作为新的业务领域 |

运行时边界可以概括为：

```text
HTTP / Scheduler / Callback
            │
            ▼
     Bootstrap adapters
            │
            ▼
 Application use cases ── Domain rules ── Domain events / outbox
            │                         │
            ▼                         ▼
     Persistence ports          External providers
            │
            ▼
      JPA repositories ── Flyway ── H2 / MySQL
```

关键工程约束：

- 数据库结构由 Flyway 管理，Hibernate 使用 `ddl-auto=validate`，不允许运行时自动建表。
- 支付回调、重放任务、导出任务和会话撤销都要求幂等或可审计。
- 外部图像审核与物流通过 Provider 接口隔离；默认开发环境使用 Mock，`staging`/`prod` 强制外部配置。
- `EventOutbox` 与公开事件契约为后续异步化和拆分部署预留边界，但当前部署形态仍是单体。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| Runtime | Java 25、Spring Boot 3.5.16 |
| Web & Security | Spring MVC、Spring Security、OAuth2 Resource Server、JWT |
| Persistence | Spring Data JPA、Hibernate、Flyway |
| Database | H2（开发/测试）、MySQL（验证/部署） |
| Cache | Redis 可选 Profile；不作为 readiness 的硬依赖 |
| Build | Maven Wrapper 3.9+ |
| Quality | JUnit 5、ArchUnit、JaCoCo、Checkstyle、Maven Enforcer |
| Integration | Testcontainers（Docker 可用时运行 MySQL 集成测试） |

## 环境要求

- JDK 25（以 `./mvnw --version` 为准）
- Git
- Maven Wrapper；不要求预装 Maven
- Docker Desktop 或 Docker Engine（运行 MySQL/Testcontainers 集成测试时需要）
- `curl` 与 `openssl`（本地启动和探针验证）

## 快速启动

### 1. H2 本地开发

Spring Boot 默认使用内存 H2、Flyway 基础迁移和 Mock Provider。密钥只在当前 shell 注入，不要写入仓库：

```bash
export SUNO_JWT_SECRET="$(openssl rand -base64 48)"
export PAYMENT_CALLBACK_SECRET="$(openssl rand -hex 32)"

./mvnw -pl suno-bootstrap -am package -DskipUnitTests=true
java -jar suno-bootstrap/target/suno-bootstrap-0.0.1-SNAPSHOT.jar
```

启动后检查：

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

readiness 只有在 Spring readiness、数据库连接和 Flyway 校验都健康时才会返回 `UP`。

### 2. MySQL 验证或部署

先创建独立数据库和最小权限应用账号，再通过环境变量注入凭据。`mysql`、`staging` 和 `prod` 不接受缺失的外部数据库凭据：

```bash
export SUNO_JWT_SECRET="$(openssl rand -base64 48)"
export PAYMENT_CALLBACK_SECRET="$(openssl rand -hex 32)"
export SUNO_DB_URL="jdbc:mysql://127.0.0.1:3306/suno_mall?useSSL=false&serverTimezone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=utf8&tinyInt1isBit=false"
export SUNO_DB_USERNAME="suno"
export SUNO_DB_PASSWORD="<least-privilege-password>"

./mvnw -pl suno-bootstrap -am package -DskipUnitTests=true
java -jar suno-bootstrap/target/suno-bootstrap-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=mysql
```

如果数据库地址已写入外部配置，也可以只切换 `--spring.profiles.active=staging` 或 `prod`。生产环境的 JWT、支付回调和外部 Provider 凭据应由 Secret Manager、进程管理器或平台密钥服务注入。

### 3. 质量验证

本地无 Docker 时运行 H2 和非 Docker 测试：

```bash
./mvnw --batch-mode --no-transfer-progress -DskipITs verify
./scripts/verify-repository.sh
./scripts/verify-docs.sh
```

Docker 可用时运行完整门禁：

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

没有 Docker 时，MySQL Testcontainers 集成测试会被跳过；跳过结果不等同于生产数据库验证。独立本地 MySQL 启动验证、迁移证据和已知限制记录在 [开发基线](docs/development/baseline.md)。

## 配置与安全基线

Spring Boot 不会自动加载 `.env`。可以参考 [`.env.example`](.env.example)，但不要提交填充后的 `.env` 或任何真实密钥。

| 变量 | 适用范围 | 用途 |
| --- | --- | --- |
| `SUNO_JWT_SECRET` | 所有启动 | JWT HMAC 签名与校验 |
| `PAYMENT_CALLBACK_SECRET` | 所有启动 | 支付回调签名校验 |
| `SUNO_DB_URL` | `mysql`/`staging`/`prod` | 外部 MySQL 连接串 |
| `SUNO_DB_USERNAME` / `SUNO_DB_PASSWORD` | `mysql`/`staging`/`prod` | 最小权限数据库账号 |
| `SUNO_REDIS_HOST` / `SUNO_REDIS_PORT` / `SUNO_REDIS_PASSWORD` / `SUNO_REDIS_DATABASE` | `redis` | 可选查询缓存 |
| `BAIDU_IMAGE_AUDIT_ENDPOINT` / `BAIDU_IMAGE_AUDIT_ACCESS_TOKEN` | `staging`/`prod` | 真实图片审核 Provider |
| `LOGISTICS_ENDPOINT` / `LOGISTICS_API_KEY` | `staging`/`prod` | 真实物流 Provider |

必须遵守的边界：

- 不使用 README、配置文件或测试数据中的 demo 凭据作为生产凭据。
- 生产数据库账号只授予应用所需的库表权限，迁移权限应与运行时账号分离。
- Actuator 的 aggregate health、metrics、info 和 Flyway 详情要求管理员认证；公开只暴露 liveness/readiness 探针。
- `dev` Profile 的 seed 用户只用于本地演示，密码采用 `{noop}` 是有意的开发约束，不能带入生产。
- 应用启动失败时优先检查密钥、数据库连接、Flyway checksum 和 Profile 组合，不要通过关闭校验绕过问题。

## API 入口

README 只保留路由分区，完整请求参数、事件契约和流程锚点见 [需求目录](docs/requirements/README.md)：

| 路由分区 | 典型能力 |
| --- | --- |
| `/api/auth` | 登录、刷新、登出、会话查询和设备撤销 |
| `/api/admin/auth` | 会话审计、安全事件汇总、风险用户和导出任务 |
| `/api/recycle` | 创建回收单、查询物流状态 |
| `/api/admin/recycle` | 回收审核、二销发布、履约、退款和审计 |
| `/api/mall` | 商品、订单、收藏、评价、投票和举报 |
| `/api/payment` | 支付回调验签、幂等落库和响应 |
| `/api/admin/payment` | 回调日志、重放队列、死信再投递和运维处置 |
| `/actuator/health/*` | liveness/readiness 探针 |

代表性请求：

```bash
# 登录获取 access token（开发 seed 仅适用于 dev 场景）
curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"user123","deviceId":"curl-test"}'

# 创建回收单
curl -fsS -X POST http://localhost:8080/api/recycle/orders \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":1001,"snCode":"SN-DEMO-001","imageUrl":"https://demo/image.jpg","wearScore":85,"recycleCount":3}'

# 查询商城商品
curl -fsS http://localhost:8080/api/mall/listings \
  -H "Authorization: Bearer $TOKEN"
```

完整 curl 示例和管理员接口请查阅 [docs/requirements](docs/requirements/README.md) 及历史接口说明；不要把生产 token、真实图片地址或支付密钥写进 shell 历史和 issue。

## 测试与交付门禁

测试按 Unit、Application、Web、Persistence、Concurrency、Provider、E2E 和 Architecture 分层。测试 fixture 由拥有业务语义的模块负责，生产 seed 不作为测试 fixture。详见 [测试策略](docs/development/testing.md)。

常用命令：

```bash
# Bootstrap 模块快速测试
./mvnw -pl suno-bootstrap -am test

# 需求目录与流程图覆盖
./mvnw -pl suno-bootstrap -am \
  -Dtest=DocumentationCatalogCoverageTest,DocumentationFlowCoverageTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./scripts/verify-requirement-flows.sh --task 13

# CI 等价门禁
./mvnw --batch-mode --no-transfer-progress verify
```

CI 工作流位于 [`.github/workflows/verify.yml`](.github/workflows/verify.yml)，对 `main` push 和 Pull Request 执行 Java 25 构建与 Maven `verify`。

## 当前阶段与后续路线

### Phase 0 已建立的基线

- 模块化 Maven Reactor、架构边界测试和文档覆盖校验。
- Flyway 迁移与 H2/MySQL Profile，JPA schema validation 和 readiness 探针。
- JWT、RBAC、Refresh Token 轮换、多设备会话、支付回调幂等和重放治理。
- 回收、二销、订单、评价、审计、配置中心和安全事件的 HTTP 适配器。
- 可重复的 Maven、仓库卫生、Checkstyle、JaCoCo 和 CI 验证路径。

### 明确的非目标与下一阶段

Phase 0 不等于所有业务安全和生产治理已经完成。下一阶段应按需求文档继续收敛：

- [Identity requirements](docs/requirements/identity.md)：refresh/session 持久化语义、RBAC 和安全事件边界。
- [Payment requirements](docs/requirements/payment.md)：回调验签、幂等账本、重放任务领取和死信治理。
- [Marketplace requirements](docs/requirements/marketplace.md)：CurrentActor、资源所有权、库存/订单/评价状态机。
- [Recycle requirements](docs/requirements/recycle.md)：服务端估值、积分、外部调用和短事务边界。
- [Operations requirements](docs/requirements/operations.md)：审计、配置、导出和运维任务。

## 贡献与开发流程

建议贡献流程：

1. 从 `main` 创建短生命周期分支，先阅读对应模块和需求文档。
2. 以用例、边界和不变量为单位修改，补充同层测试与文档流程锚点。
3. 本地执行 `./mvnw -DskipITs verify`、仓库校验和文档校验；Docker 可用时再运行完整 `verify`。
4. 提交信息使用清晰的动作式描述，Pull Request 说明行为变化、迁移影响、验证证据和未覆盖风险。

详细约定见 [开发工作流](docs/development/workflow.md)、[架构说明](docs/architecture/modules.md) 和 [项目开发文档](docs/项目开发文档.md)。

## License

当前仓库未声明独立开源许可证。若要对外发布，请先补充根目录 `LICENSE` 并在此处更新授权条款。
