# Recycle Mall — C2B2C 二手循环交易平台

> C2B2C 二手商品循环交易平台（第一阶段：主体框架 + 持久层落库）

## 项目概览

Recycle Mall 是一个完整的二手商品循环交易平台，覆盖从回收估价、物流追踪到二销上架、C 端购买的全链路闭环。项目采用 Spring Boot 3.5 单体架构，内置 H2/MySQL 双数据源切换，适合快速联调与近生产环境验证。

### 核心业务能力

| 模块 | 能力 |
|------|------|
| **回收流程** | 图片审核 → SN 解析 → 自动估价 → 生成回收单据 |
| **物流追踪** | 物流单号生成、状态查询、外部平台对接 |
| **积分体系** | 积分规则计算与流水落库 |
| **二销闭环** | 后台发布二销商品、C 端下单、支付幂等、取消、退款、履约发货 |
| **订单管理** | 超时自动关单、自动确认收货、订单概览统计 |
| **评价系统** | 首评/追评、商家回复、有用性投票、举报处置、智能排序 |
| **支付回调** | 回调签名校验、幂等防重、异步重放队列、死信再投递 |
| **配置中心** | 评价策略/异常码/告警降噪规则热更新、模块化增量同步 |
| **认证鉴权** | JWT + RBAC、Refresh Token 轮换、重放防护、多设备会话管理 |
| **审计日志** | 全链路操作审计、CSV 导出、分页查询 |
| **安全监控** | 安全事件汇总/时间线/风险用户 TopN、异步导出任务 |

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 25 |
| 框架 | Spring Boot | 3.5.0 |
| 安全 | Spring Security + OAuth2 Resource Server + JWT | 随 Boot 版本管理 |
| 模板 | Freemarker | 随 Boot 版本管理 |
| 持久层 | Spring Data JPA | 随 Boot 版本管理 |
| 数据库 | H2（开发）/ MySQL（生产） | 随 Boot 版本管理 |
| 构建 | Maven | 3.9+ |

## 快速启动

```bash
# 默认 H2 内存库启动
mvn spring-boot:run

# MySQL 环境启动
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

启动后访问 `http://localhost:8080`。

### 环境配置

| 环境 | Profile | 数据库 | 说明 |
|------|---------|--------|------|
| 本地开发 | 默认 | H2 内存库 | SQL 初始化开启，适合快速联调 |
| 本地 MySQL | `mysql` | MySQL | `ddl-auto=update`，适合近真实验证 |
| 测试/生产 | `mysql` + 外置配置 | MySQL | 独立库与密钥，关闭 demo 凭据 |

### 演示账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `admin123` |
| 普通用户 | `alice` | `user123` |
| 普通用户 | `bob` | `user123` |

> ⚠️ 演示数据使用 `{noop}` 明文密码，生产环境请改为 `{bcrypt}` 加密。

## 项目结构

```
src/main/java/com/recycle/mall/
├── RecycleMallApplication.java       # 启动入口
├── common/                           # 通用响应与缓存契约
│   ├── ApiResponse.java
│   └── CacheContract.java
├── config/
│   └── SecurityConfig.java           # Spring Security + JWT 配置
├── controller/                       # HTTP 接口层
│   ├── AuthController.java           # 登录/刷新/登出/会话
│   ├── AdminAuthController.java      # 管理员会话审计/安全事件/导出
│   ├── RecycleController.java        # C 端回收入口
│   ├── AdminRecycleController.java   # 后台回收/评价/配置中心
│   ├── ResaleMallController.java     # C 端二销商城
│   ├── PaymentCallbackController.java # 支付回调
│   ├── AdminPaymentController.java   # 支付重放管理
│   ├── GlobalExceptionHandler.java   # 全局异常处理
│   └── *Scheduler.java               # 定时任务
├── dao/                              # JPA Repository 层
├── entity/                           # JPA 实体
├── provider/                         # 外部平台适配（Mock/Real 切换）
│   ├── ImageAuditProvider.java       # 图像审核接口
│   └── LogisticsProvider.java        # 物流查询接口
└── service/                          # 业务逻辑层
    ├── AuthApplicationService.java   # 认证鉴权
    ├── RecycleApplicationService.java # 回收流程编排
    ├── ResaleOrderService.java       # 二销订单
    ├── ResaleListingService.java     # 二销商品
    ├── ResaleReviewService.java      # 评价系统
    ├── PaymentReplayService.java     # 支付重放队列
    ├── PaymentSignatureService.java  # 支付签名
    ├── ConfigCenterService.java      # 配置中心
    ├── AuditLogService.java          # 审计日志
    ├── ValuationService.java         # 估价算法
    └── support/                      # 辅助工具
        ├── AuditLogHelper.java
        ├── I18nHelper.java
        └── VersionHelper.java
```

## API 接口总览

### 认证鉴权 `/api/auth`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/auth/login` | 登录（返回 JWT + Refresh Token） | 公开 |
| POST | `/api/auth/refresh` | 刷新 Access Token | 公开 |
| GET | `/api/auth/me` | 查询当前用户 | 登录 |
| POST | `/api/auth/logout` | 登出（黑名单 + 可选撤销 Refresh Token） | 登录 |
| GET | `/api/auth/sessions` | 查询活跃设备会话 | 登录 |
| POST | `/api/auth/sessions/revoke-device` | 按设备下线 | 登录 |
| POST | `/api/auth/sessions/revoke-all` | 全设备下线 | 登录 |

### 管理员认证 `/api/admin/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/auth/sessions?username=` | 查看指定用户会话 |
| POST | `/api/admin/auth/sessions/revoke-device` | 管理员按设备强制下线 |
| POST | `/api/admin/auth/sessions/revoke-all` | 管理员强制全设备下线 |
| GET | `/api/admin/auth/security-events/summary` | 安全事件汇总 |
| GET | `/api/admin/auth/security-events/timeline` | 安全事件时间序列 |
| GET | `/api/admin/auth/security-events/risk-users-top` | 风险用户 TopN |
| GET | `/api/admin/auth/security-events/export` | 同步导出（CSV/JSON） |
| POST | `/api/admin/auth/security-events/export/tasks` | 创建异步导出任务 |
| GET | `/api/admin/auth/security-events/export/tasks/{id}` | 查询导出任务状态 |
| GET | `/api/admin/auth/security-events/export/tasks/{id}/download` | 下载导出结果 |
| POST | `/api/admin/auth/security-events/export/tasks/{id}/retry` | 重试失败任务 |
| POST | `/api/admin/auth/security-events/export/tasks/cleanup` | 清理过期任务 |

### 回收流程 `/api/recycle`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/recycle/orders` | 创建回收单 |
| GET | `/api/recycle/logistics/status?trackingNo=` | 查询物流状态 |

### 后台回收管理 `/api/admin/recycle`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/recycle/orders` | 查看回收单列表 |
| PATCH | `/api/admin/recycle/orders/review` | 审核回收单 |
| POST | `/api/admin/recycle/listings/publish` | 发布二销商品 |
| POST | `/api/admin/recycle/resale-orders/deliver` | 确认发货 |
| POST | `/api/admin/recycle/resale-orders/refund` | 退款 |
| POST | `/api/admin/recycle/resale-orders/auto-confirm-receipt` | 自动确认收货 |
| GET | `/api/admin/recycle/audit-logs` | 查询审计日志 |
| GET | `/api/admin/recycle/audit-logs/page` | 分页查询审计日志 |
| GET | `/api/admin/recycle/audit-logs/export` | 导出审计日志 CSV |

### 评价管理 `/api/admin/recycle/review-*`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/recycle/review-reports` | 查看举报工单 |
| POST | `/api/admin/recycle/review-reports/process` | 处理举报工单 |
| POST | `/api/admin/recycle/review-reports/process-batch` | 批量处理举报 |
| GET | `/api/admin/recycle/review-risk/summary` | 评价风控概览 |
| GET | `/api/admin/recycle/review-risk/timeline` | 风控时间线 |
| GET | `/api/admin/recycle/review-risk/top-listings` | 高风险商品 TopN |
| GET | `/api/admin/recycle/review-strategy` | 查询评价策略 |
| POST | `/api/admin/recycle/review-strategy/update` | 热更新评价策略 |

### 配置中心 `/api/admin/recycle/config-center`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/recycle/config-center/bundle` | 聚合包（评价策略 + 异常码 + 启动计划） |
| GET | `/api/admin/recycle/config-center/modules` | 模块摘要（digest） |
| POST | `/api/admin/recycle/config-center/module-diff` | 增量差异比对 |
| GET | `/api/admin/recycle/config-center/module/{name}` | 按模块拉取配置 |
| GET | `/api/admin/recycle/error-codes/global` | 全局异常码字典 |
| GET | `/api/admin/recycle/degrade-actions/dictionary` | 降级动作字典 |
| GET | `/api/admin/recycle/alert-noise-rules` | 告警降噪规则 |
| POST | `/api/admin/recycle/alert-noise-rules/update` | 热更新降噪规则 |

### C 端商城 `/api/mall`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/mall/listings` | 在售商品列表（支持筛选排序） |
| POST | `/api/mall/orders` | 创建订单 |
| POST | `/api/mall/orders/cancel` | 取消订单 |
| POST | `/api/mall/orders/confirm-receipt` | 确认收货 |
| GET | `/api/mall/orders` | 买家订单列表（分页/筛选） |
| GET | `/api/mall/orders/status-dictionary` | 订单状态字典 |
| GET | `/api/mall/orders/summary` | 订单概览统计 |
| GET | `/api/mall/orders/{orderNo}/track` | 订单履约轨迹 |
| POST | `/api/mall/favorites/add` | 添加收藏 |
| POST | `/api/mall/favorites/remove` | 取消收藏 |
| GET | `/api/mall/favorites` | 我的收藏 |
| POST | `/api/mall/reviews/create` | 提交评价 |
| POST | `/api/mall/reviews/append` | 追评 |
| POST | `/api/mall/reviews/reply` | 商家回复 |
| GET | `/api/mall/reviews` | 商品评价列表 |
| POST | `/api/mall/reviews/vote-useful` | 有用性投票 |
| POST | `/api/mall/reviews/report` | 举报评价 |

### 支付回调与重放 `/api/payment` & `/api/admin/payment`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/payment/callback` | 支付回调（签名校验 + 幂等） |
| GET | `/api/admin/payment/callback-logs` | 查询回调日志 |
| POST | `/api/admin/payment/callback-logs/replay` | 同步重放回调 |
| POST | `/api/admin/payment/callback-logs/replay/enqueue` | 异步入队重放 |
| POST | `/api/admin/payment/callback-logs/replay/consume` | 手工消费重放队列 |
| GET | `/api/admin/payment/replay-tasks` | 分页查询重放任务 |
| GET | `/api/admin/payment/replay-tasks/summary` | 重放队列摘要 |
| GET | `/api/admin/payment/replay-tasks/health` | 队列健康状态 |
| GET | `/api/admin/payment/replay-tasks/diagnosis` | 一键巡检与处置建议 |
| POST | `/api/admin/payment/replay-tasks/auto-handle` | 半自动处置 |
| POST | `/api/admin/payment/replay-tasks/requeue` | 单条再投递 |
| POST | `/api/admin/payment/replay-tasks/requeue/dead` | 批量再投递 DEAD |

### 商品详情页

```
http://localhost:8080/products/{productId}.html
```

## 接口示例

### 登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123","deviceId":"web-chrome-01"}'
```

返回：

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "refreshToken": "4f...b1",
    "refreshExpiresIn": 604800,
    "username": "admin",
    "role": "ADMIN"
  }
}
```

### 创建回收单

```bash
curl -X POST http://localhost:8080/api/recycle/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "userId":1001,
    "snCode":"SN-123456",
    "imageUrl":"https://demo/image.jpg",
    "wearScore":85,
    "recycleCount":3
  }'
```

### 创建 C 端订单

```bash
curl -X POST http://localhost:8080/api/mall/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"buyerUserId":1002,"listingId":1}'
```

### 提交评价

```bash
curl -X POST http://localhost:8080/api/mall/reviews/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "buyerUserId":1002,
    "rating":5,
    "content":"成色很好，和描述一致"
  }'
```

## 估价算法

采用"规则表匹配 + 兜底策略"双层机制：

1. **输入因子**：品牌、型号、生产日期（SN 解析）、磨损分（图片审核）
2. **规则匹配**：按品牌/型号精确或 `ALL` 通配查询规则表，通配越少优先级越高
3. **优先级**：精确品牌+精确型号 > 精确品牌+ALL > ALL+精确型号 > ALL+ALL
4. **兜底策略**：规则未命中时按机龄内置估价（≤18月 GOOD/1800，≤36月 MEDIUM/1200，>36月 UNQUALIFIED/300）

## 认证鉴权

### JWT 流程

```
登录 → 签发 Access Token (HS256) + Refresh Token (绑定设备)
       ↓
请求携带 Bearer Token → JwtDecoder 校验签名 + 有效期 + jti 黑名单
       ↓
Refresh Token 过期前 → POST /api/auth/refresh 换发新 Token 对（旧 Refresh Token 失效）
       ↓
检测 Refresh Token 重放 → 撤销该用户全部活跃 Refresh Token
```

### RBAC 规则

| 路径 | 权限 |
|------|------|
| `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/payment/callback`, `/products/**` | 公开 |
| `/api/admin/**` | `ROLE_ADMIN` |
| `/api/recycle/**`, `/api/mall/**`, `/api/auth/me`, `/api/auth/logout` | 已登录 |

### 统一错误返回

- **401**：`{"success":false,"message":"请先登录","data":null}`
- **403**：`{"success":false,"message":"无权限访问该资源","data":null}`

## 支付重放队列

```
回调日志 → 入队 → PENDING → PROCESSING → SUCCESS
                                    ↓ (失败+重试)
                              PENDING (退避等待)
                                    ↓ (超过最大重试)
                                   DEAD → 人工再投递
```

- **退避公式**：`backoff = min(base × 2^(retry-1), max)`，默认 base=5s, max=300s
- **去重约束**：同一 `callbackLogId` 在 PENDING/PROCESSING 下仅允许一条任务
- **半自动处置**：`POST /api/admin/payment/replay-tasks/auto-handle`（自动消费 + 可选自动重投 DEAD）
- **幂等保护**：auto-handle 基于 traceId 做短窗口幂等

## 配置中心

支持模块化增量同步，前端可按 `groupMeta + fieldMeta` 自动渲染配置表单：

1. `GET /modules` → 获取模块摘要（digest）
2. `POST /module-diff` → 比对本地与服务端差异
3. `GET /module/{name}` → 按需拉取变化模块
4. `POST /review-strategy/update` 或 `/alert-noise-rules/update` → 热更新配置

所有管理端配置接口支持 **ETag 缓存协商**（`If-None-Match` / `304 Not Modified`）。

## 缓存协商

以下接口支持 HTTP 缓存协商：

- 管理端：`review-strategy`、`error-codes/global`、`config-center/bundle`、`config-center/module/*`
- C 端：`/api/mall/orders`、`/api/mall/orders/status-dictionary`、`/api/mall/orders/summary`

请求头：`If-None-Match`，响应头：`ETag`、`Last-Modified`、`X-Cache-Digest`，命中返回 `304`。

## 外部平台配置

默认使用 Mock，切换真实对接：

```yaml
provider:
  image-audit:
    mode: real
    baidu:
      endpoint: "https://your-baidu-audit-endpoint"
      access-token: "your-access-token"
  logistics:
    mode: real
    endpoint: "https://your-logistics-endpoint"
    api-key: "your-api-key"
```

## 关键配置项

```yaml
# JWT
security:
  auth:
    jwt:
      secret: recycle-mall-demo-jwt-secret-key-please-change  # 生产必须替换
      expire-minutes: 120
      refresh-expire-minutes: 10080
    export-task:
      retain-days: 7
      cleanup-fixed-delay-ms: 3600000
      max-running: 3
      default-max-retry: 2
      running-timeout-minutes: 10

# 支付重放
payment:
  callback:
    replay-consume-fixed-delay-ms: 30000
    replay-consume-batch-size: 20
    replay-max-retry: 3
    replay-backoff-base-seconds: 5
    replay-backoff-max-seconds: 300
    replay-health-pending-threshold: 100
    replay-health-dead-threshold: 10

# 评价
mall:
  review:
    append-window-days: 30
```

## 审计日志

所有关键操作自动写入 `operation_audit_log`，支持按 `actionType`/`targetId` 检索。

### 主要审计动作类型

| 类别 | 动作类型 |
|------|----------|
| 认证 | `AUTH_LOGIN_SUCCESS`, `AUTH_LOGOUT`, `AUTH_REFRESH_SUCCESS`, `AUTH_REFRESH_REPLAY_BLOCKED` |
| 会话 | `AUTH_SESSION_REVOKE_DEVICE`, `AUTH_SESSION_REVOKE_ALL`, `AUTH_ADMIN_SESSION_REVOKE_DEVICE`, `AUTH_ADMIN_SESSION_REVOKE_ALL` |
| 导出 | `AUTH_EXPORT_TASK_CREATED`, `AUTH_EXPORT_TASK_SUCCESS`, `AUTH_EXPORT_TASK_FAILED`, `AUTH_EXPORT_TASK_RETRY`, `AUTH_EXPORT_TASK_TIMEOUT` |
| 支付 | `RESALE_ORDER_PAY`, `PAYMENT_REPLAY_HEALTH_QUERY`, `PAYMENT_REPLAY_DIAGNOSIS_QUERY` |
| 清理 | `PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP`, `PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN` |

## 测试

```bash
# 全量测试
mvn test

# 指定测试类
mvn -Dtest=AuthSecurityIntegrationTest test
mvn -Dtest=RecycleApplicationServiceQueryAuditActionsTest test

# 使用项目脚本
./scripts/run-tests.sh all        # 全量
./scripts/run-tests.sh query      # query-audit-actions 契约测试
./scripts/run-tests.sh app        # application 层测试
./scripts/run-tests.sh self-check # 脚本自检
./scripts/run-tests.sh list       # 列出可用范围

# CI 入口
./scripts/run-tests-ci.sh         # 默认 query
./scripts/run-tests-ci.sh all     # 全量
./scripts/run-tests-ci.sh query --json  # 机器可读输出
```

## 生产部署建议

- 🔑 替换 `security.auth.jwt.secret`，使用安全配置中心管理密钥
- 🔑 替换 `payment.callback.secret`
- 🗄️ 使用最小权限数据库账号
- 📋 固化审计日志与导出任务保留策略
- 🔒 密码存储改为 `{bcrypt}` 加密
- 📊 接入监控告警（支付重放队列健康、认证安全事件、配置同步状态）

## 下一阶段规划

1. 接入真实支付网关 SDK（统一下单、验签、公钥轮换、证书管理）
2. 增加支付与重放监控指标（成功率、死信增长率、平均重放时延）并接告警
3. 完善 C 端二销评价能力（图片评价、评价举报、评价有用性投票）
4. 增加关键链路集成测试（回调签名、nonce 重放、退避重试、死信再投递）

## License

Private — All rights reserved.
