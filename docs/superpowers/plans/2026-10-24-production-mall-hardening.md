# 生产级商城完善方案（2026-10-24）

> 状态：**待确认** —— 本文档是全面审计后的补全方案，经确认后按里程碑执行。
> 审计范围：`suno`（Java 后端，9 模块 Maven Reactor）+ `suno-frontend`（storefront / admin / miniprogram 三端 + shared 共享层）。

## 1. 目标

在不推翻现有 Phase 0 基线的前提下，把当前"演示可用"的回收+二销闭环补全为**可真实运营的生产级商城服务**：

1. 用户无需数据库种子即可自助注册、登录、交易。
2. 商城标配能力齐全：搜索/分类、购物车、收货地址、优惠券、售后、通知、积分可见。
3. 支付从模拟签名升级为真实网关收单 + 异步回调对账。
4. 运营后台覆盖商品、订单、用户、优惠券、报表的日常运营动作。
5. 生产治理达标：API 文档、限流防刷、可观测性、对账与审计。

## 2. 审计结论

### 2.1 后端已有能力（保留并复用）

| 领域 | 已实现（代表符号） |
| --- | --- |
| 身份 | JWT(HS256)+jti 黑名单、refresh 轮换、多设备会话、安全事件与导出任务（`AuthController`、`AuthApplicationService`、`AuthSessionService`） |
| 商品/订单 | 列表过滤排序、原子库存预留防超卖（`ResaleListingRepository#reserveOneStock`）、下单/支付/取消/确认收货、订单摘要与状态字典、ETag 缓存 |
| 评价 | 创建/追评/商家回复/有用投票/举报 + 风控摘要（`ResaleReviewService`） |
| 回收 | 创建回收单、物流状态、审核/估价/上架/发货/退款/自动确认收货（`RecycleApplicationService`、调度器） |
| 支付 | 回调 HMAC 验签、nonce、幂等账本、回调日志、重放任务 + 死信再投递 + 管理处置（`PaymentCallbackController`、`PaymentReplayService`） |
| 运维 | 审计日志、配置中心、告警降噪、告警/审计导出（`AdminAuthController`、`AdminRecycleController`） |
| 工程 | Flyway（`ddl-auto=validate`）、H2/MySQL/Redis Profile、按域组合的 `@Transactional` 注解、ArchUnit、bootstrap 内 21 个测试类、`use-cases.yaml` 目录与文档一致性测试 |

### 2.2 前端已有能力

- **storefront**（6 页）：首页/市场/商品详情（收藏、下单、评价列表、举报）/回收提交/订单（状态筛选、支付、取消、确认收货、物流追踪、评价弹窗）/账户（会话管理、收藏数）。
- **admin**（3 页）：运营指标/回收审核工作台/风险与支付摘要。
- **miniprogram**（6 页）：首页/市场/商品/回收/订单/账户，与 storefront 共享 shared 层。
- **shared**：统一 API client（超时、ETag、401 刷新）、demo 模式、支付 HMAC 签名、类型映射。

### 2.3 缺口清单（按严重度分级）

#### P0 —— 阻断真实运营的缺口

| # | 缺口 | 证据 |
| --- | --- | --- |
| 1 | **无注册接口**：`AuthController` 只有 login/me/sessions/refresh/logout，用户依赖 dev 种子 | `AuthController.java` |
| 2 | **支付为模拟**：`/api/mall/orders/pay` 是联调占位（TODO 注释），当前 HMAC 仅自证身份，无真实收单 | `ResaleMallController.java:125` |
| 3 | **无单商品详情接口**：前端 `getListing` 拉全量列表再客户端查找 | `packages/shared/src/api.ts:98` |
| 4 | **无用户回收历史**：前端非 demo 模式直接 reject「用户回收记录接口未提供」 | `api.ts:144` |
| 5 | **无搜索/分类**：`/api/mall/listings` 仅支持 grade/sort/minStock；`suno_category` 表零使用 | `ResaleMallController.java:60` |

#### P1 —— 商城标配能力缺失（有表无功能或无表）

| # | 缺口 | 现状 |
| --- | --- | --- |
| 6 | 购物车 | 无表、无接口、无页面 |
| 7 | 收货地址管理 | `suno_user_address` 表 + `UserAddressEntity` 存在，无 Repository/Service/Controller |
| 8 | 优惠券 | `suno_coupon` / `suno_coupon_user` 表 + 实体存在，零功能 |
| 9 | 用户端售后/退款申请 | `suno_refund_order` 表 + 实体存在；管理端有 refund 动作，买家无法发起售后 |
| 10 | 站内通知 | `suno_notification` 表 + 实体存在，零接口 |
| 11 | 积分流水查询 | `PointsLedgerRepository`/`PointsService` 存在，无 HTTP 端点 |
| 12 | 订单收货信息 | 订单无收货人/地址快照字段，发货目的地无处落地 |

#### P2 —— 运营与多端缺口

| # | 缺口 | 现状 |
| --- | --- | --- |
| 13 | admin 缺商品管理（改价/下架/恢复） | 只有发布 |
| 14 | admin 缺订单管理页（全量订单、发货、退款处置界面化） | 动作散落在回收工作台 |
| 15 | admin 缺用户管理（列表/封禁/角色） | 无 |
| 16 | admin 缺优惠券管理与经营报表 | 无 |
| 17 | miniprogram 未对齐新增能力 | 随 M2/M3 同步 |
| 18 | SN 解析为硬编码 TODO | `SnParseService.java:12` |
| 19 | 商品图片链路不完整 | `suno_product_image` 表存在，列表/详情基本靠前端 placeholder |

#### P3 —— 生产治理缺口

| # | 缺口 | 说明 |
| --- | --- | --- |
| 20 | 无 API 文档 | 未引入 springdoc-openapi；契约只存在于 use-cases.yaml |
| 21 | 无限流防刷 | 登录/注册/下单/支付/回调均无速率限制 |
| 22 | 无对账机制 | 支付回调、订单状态、幂等账本之间无日终对账任务 |
| 23 | 领域模块空壳 | identity/recycle/marketplace/payment 仅 `package-info.java`，全部实现在 `suno-bootstrap`，与 README 宣称的迁移边界不符 |
| 24 | 可观测性薄弱 | 无结构化访问日志/慢 SQL 埋点/业务指标导出 |

## 3. 路线图：四个里程碑

> 命名说明：现有需求文档使用 Phase 1-5 表示**分域**（Identity/Payment/Marketplace/Recycle/Operations）收敛阶段；本方案使用 **M1-M4** 表示跨域的交付里程碑，二者正交。每个里程碑交付后都必须保持 `use-cases.yaml` 目录、需求锚点、测试与 `./scripts/verify-*.sh` 全绿。

### M1 —— 用户与商品主链路补全（P0 清零）

> **状态：已完成（2026-10-24）**。后端 `./mvnw -DskipITs verify`（86 tests）+ `verify-docs.sh`/`verify-repository.sh` 全绿；前端 pnpm test/lint/typecheck/build（storefront/admin/miniprogram）全绿。
>
> 实施偏差（较原方案）：
> 1. 积分流水路径为 `GET /api/recycle/points/ledger`（原方案写 `/api/points/ledger`），复用既有 `/api/recycle/**` 认证匹配器与域前缀。
> 2. 浏览类接口（`GET /api/mall/listings`、`/api/mall/listings/{id}`、`/api/mall/categories`）改为匿名可读（MKT-020/038/039 permission 更新为 public-explicit），与前端市场页无 token 浏览一致，符合“先逛后登录”的商城语义；下单/收藏/评价仍要求认证。
> 3. 附带修复两项存量缺陷：`suno_user_account` 手工主键无法自助注册（新增 V1001 发号表 + JPA TableGenerator，初始 10001）；`CacheConfig` 无条件依赖 Redis 导致无 Redis 时审核/发布流程失败（redis profile 才启用 RedisCacheManager，其余环境进程内兜底，兑现 README“Redis 可选”声明）。
> 4. 新增路由按契约先登记后实现：use-cases.yaml +128 条目（IDN-008、REC-003/004、MKT-038/039），两个契约测试计数同步。

**后端新增/变更**

| 接口 | 说明 |
| --- | --- |
| `POST /api/auth/register` | 用户名+密码注册，BCrypt 加密，角色 USER；用户名唯一约束与冲突错误码 |
| `GET /api/mall/listings/{id}` | 单品详情：价格/成色/库存/图片/服务标签/评价摘要 |
| `GET /api/mall/listings`（增强） | 新增 `keyword`（先 LIKE、MySQL 可升级 FULLTEXT）、`categoryId`、标准分页参数 |
| `GET /api/categories` | 分类树/列表（激活 `suno_category`） |
| `GET /api/recycle/orders/my` | 当前用户回收历史（分页），替换前端硬 reject |
| `GET /api/points/ledger` | 当前用户积分流水分页 |

数据库迁移：回收单/商品列表如缺 `category` 关联或搜索列，按 Flyway 基线规范新增增量迁移；不修改既有基线迁移。

**前端**：注册入口（登录面板升级为登录/注册双 tab）；市场页搜索框 + 分类筛选；商品详情改调单品接口；账户页增加回收记录与积分流水区块；shared 层新增对应 API 封装与类型。

**验收**：新用户可自助注册→登录→搜索→看详情→下单（走既有 mock 支付）；上述每个新接口在 `use-cases.yaml` 登记 ID 并有 Web 层测试；三端 demo 模式数据同步补充。

### M2 —— 交易配套能力（购物车/地址/优惠券/售后/通知）

> **状态：已完成（2026-10-24）**。后端 18 个新路由（IDN-020~024 地址、MKT-040~042 购物车、MKT-050~052 优惠券、MKT-060~062 售后、OPS-050~053 通知），96 测试 verify 全绿；前端购物车页、结算弹窗（地址+选券+金额明细）、通知中心、账户页地址/券/售后管理、订单页售后入口全部落地，三端构建通过。
>
> 实施偏差：1）新增计划外的 `POST /api/notifications/read-all`（OPS-053）便利接口；2）通知当前为业务事务内同步写库，outbox 事件化保留在 M4/长期（需求文档 Gaps 已标注）；3）小程序端购物车/地址/通知 UI 对齐顺延到 M3 批次（shared 层 API 已就绪）。

**后端新增**

| 能力 | 接口 |
| --- | --- |
| 购物车（新表 `suno_cart_item`） | `GET/POST/DELETE /api/cart`、`DELETE /api/cart/{itemId}`（二手单件，数量恒 1，加入前校验上架与库存） |
| 地址 | `GET/POST/PUT/DELETE /api/addresses`、`POST /api/addresses/{id}/default`；下单必选地址，订单落收货人快照（新列） |
| 优惠券 | `GET /api/coupons`（可领列表）、`POST /api/coupons/{id}/claim`、`GET /api/coupons/mine`、下单传 `couponUserId` 核销；订单增加优惠金额与应付金额字段，幂等防重复核销 |
| 售后 | `POST /api/mall/orders/{orderNo}/refund-apply`、`GET /api/mall/refunds`、`POST /api/mall/refunds/{id}/cancel`；打通 `suno_refund_order` 与既有管理端退款动作 |
| 通知 | `GET /api/notifications`、`POST /api/notifications/{id}/read`、`GET /api/notifications/unread-count`；订单状态变更/审核结果/售后进度事件写入 `suno_notification` |

**前端**：购物车页与结算流程（选地址→选券→金额明细→逐件/合并下单，按决策点 2 的结论实现）；地址管理界面；我的优惠券；订单页售后申请入口与售后单列表；导航通知铃铛与通知中心；miniprogram 同步购物车/地址/通知。

**验收**：完整的"加入购物车→结算→选地址→用券→下单→支付→申请退款→管理端处置→通知触达"链路可用；优惠券核销与退款回滚券有并发测试；订单金额 = 原价 − 券抵扣 的服务端校验测试。

### M3 —— 支付真实化与运营后台补全

> **状态：已完成（2026-10-24）**。支付：纯 JDK RSA2 支付宝适配器（无 SDK，规避依赖门禁）——`AlipaySignatureService` 签名/验签、`AlipayPagePayService` 生成 page.pay 收银台跳转、`PAY-003 POST /api/payment/alipay/notify` 异步通知（验签+app_id+金额三重校验、幂等落账、渠道信息回填订单，V3002 增列 pay_channel/trade_no/paid_at）；`payment.alipay.enabled` 开关启用，关闭时保留 HMAC Mock 网关；启动期缺密钥即失败。运营后台：商品管理（分页/改价/上下架）、订单管理（全量分页/关键词/详情含收货与渠道信息）、用户管理（搜索/封禁联动会话撤销）、优惠券管理（创建/停用）、经营报表（GMV/退款率/回收量/趋势），共 12 个新路由；admin 前端新增五个页面与导航。后端 107→109 测试、前端三端构建全绿。
>
> 实施偏差：独立渠道交易表与支付宝对账单下载（bill.downloadurl）查单补偿未纳入本批（payment.md Gaps 已标注），退款仍走原路经；沙箱联调需真实密钥（`ALIPAY_*` 环境变量），代码路径已由测试密钥全链路验证。

**支付**（按决策点 1 的网关选择实施）：

- `POST /api/mall/orders/pay` 改为返回真实支付凭据（支付宝表单/二维码或微信预支付参数）。
- 回调切换为网关官方验签（RSA2 / 平台证书），保留既有幂等账本与回调日志；自建 HMAC 降级为内部联调通道。
- 增加支付单实体与渠道流水号映射、支付超时关单联动（复用既有 auto-close 调度器）。

**运营后台**：

| 页面/能力 | 内容 |
| --- | --- |
| `GET/POST /api/admin/mall/listings/*` | 商品管理：全量列表、改价、下架/恢复、库存修正（带审计） |
| `GET /api/admin/mall/orders/*` | 订单管理：全量订单分页筛选、详情、发货（真实物流 Provider 或手工单号）、退款处置界面化 |
| `GET /api/admin/users/*` | 用户管理：列表/详情/封禁/解封/角色调整（登录拦截 + 会话撤销联动） |
| `GET/POST /api/admin/coupons/*` | 优惠券管理：创建/编辑/停用/定向发放/核销报表 |
| `GET /api/admin/reports/*` | 经营报表：GMV、订单转化、回收量、退款率趋势（复用现有 summary 聚合模式） |

**前端 admin**：新增 `/products`、`/orders`、`/users`、`/coupons`、`/reports` 五个页面与导航重构。

**验收**：沙箱环境完成真实下单→支付→回调→发货→确认收货→退款全链路；每个 admin 动作有权限测试（RBAC）与审计日志；报表口径有测试快照。

### M4 —— 生产加固（治理与文档）

> **状态：已完成（2026-10-24）**。1）springdoc-openapi：`/v3/api-docs` 与 swagger-ui 仅 ADMIN 可见（SecurityConfig），契约目录测试排除基础设施端点；2）限流：`RateLimitFilter` 固定窗口（IP+路径，默认 60 次/分钟，可配），覆盖登录/注册/下单/支付/双回调，超限 429 + `RATE_LIMITED`；3）对账：`PAY-S004` 调度器（默认 6 小时）核对 PAID/REFUNDED 订单的支付凭证（回调日志/渠道交易号），无凭证订单写 `RECONCILE_MISMATCH` 审计；4）可观测性：`RequestObservabilityFilter` 为每个请求生成 X-Request-Id（MDC+响应头）并记录慢请求；5）SN 解析与图片审核维持既有 Provider 双实现（mock/real 配置切换，`Real*` 类已存在），生产接入厂商凭据即启用；6）模块回迁：按既定决策**不纳入本次**（identity/recycle/marketplace/payment 空壳的现状已在 README/架构文档标注，待业务稳定后独立立项）。后端 109 测试 + verify + 文档脚本全绿。
>
> 至此 M1-M4 全部交付：需求目录 160 条目（128 起步），HTTP 路由 99→130，调度器 6→7。

1. **API 文档**：引入 springdoc-openapi，swagger-ui 仅 ADMIN 可见；前端可选从 openapi.json 生成类型替代手写 `types.ts`。
2. **限流防刷**：登录/注册/下单/支付/回调接口加 Redis 令牌桶（无 Redis 时降级为进程内限流）；注册加邀请码或图形验证码开关。
3. **对账**：日终任务比对回调日志、订单支付状态、幂等账本三者一致性，差异写审计并告警（复用 outbox/调度器模式）。
4. **可观测性**：结构化访问日志（含 traceId）、慢 SQL 阈值日志、核心业务指标（下单量/支付成功率/回调延迟）经 actuator 暴露。
5. **SN 解析与图片审核真实化收尾**：SN 规则库或厂商接口接入；`suno_product_image` 图片链路贯通（上传→审核→展示）。
6. **模块回迁（可选，独立评估）**：将 bootstrap 内按域代码迁回 identity/recycle/marketplace/payment 模块，兑现 README 宣称的边界；建议作为 M4 之后独立立项，避免与业务补全互相阻塞。

## 4. 工程与文档契约（每个里程碑的完成定义）

1. **先目录后代码**：新路由先在 `docs/requirements/use-cases.yaml` 分配稳定 ID、补 requirementDoc 锚点与 Mermaid 流程，再写实现（`DocumentationCatalogCoverageTest` 强制目录与实际路由一致）。
2. **迁移规范**：只增不改既有基线迁移，新表/新列走各域编号段（identity V1xxx / recycle V2xxx / marketplace V3xxx / payment V4xxx / operations V5xxx）的增量迁移。
3. **测试门禁**：每个新接口至少 Web 层契约测试 + Service 层业务测试；金额/库存/券核销类逻辑必须有并发测试；`./mvnw -DskipITs verify` 与 `./scripts/verify-docs.sh`、`./scripts/verify-repository.sh` 全绿。
4. **前端门禁**：pnpm 构建 + vitest 全绿；demo 模式为所有新接口补演示数据，保持离线可体验。
5. **提交切分**：按里程碑内的能力域拆 PR（例如 M1 拆注册/搜索/详情/历史四批），每个 PR 自带文档锚点更新。

## 5. 风险与关键决策点（需确认）

| # | 决策点 | 选项与建议 |
| --- | --- | --- |
| 1 | 支付网关 | A. 支付宝（已有沙箱计划文档，**建议**）；B. 微信支付；C. 双通道（工作量约 ×1.6） |
| 2 | 购物车与订单模型 | A. 保持一单一商品 + 购物车逐件下单（**建议**，二手单件天然适配，改动最小）；B. 演进 order/order_item 合并支付（支持一单多件+合并运费，改动大） |
| 3 | 注册方式 | A. 用户名+密码（**建议**先行）；B. 手机号+短信验证码（需短信服务商成本）；C. 后续再加微信登录 |
| 4 | 优惠券范围 | A. M2 只做满减券（**建议**）；B. 满减+折扣+运费券全量 |
| 5 | 模块物理回迁 | A. 不纳入本次，仅文档标注差距（**建议**）；B. M4 末期执行大迁移 |
| 6 | 里程碑顺序 | 默认 M1→M2→M3→M4；若支付资质已就绪，可将 M3 支付部分提前与 M2 并行 |

## 6. 预估规模（供排期参考）

| 里程碑 | 后端新接口 | 新表/迁移 | 前端页面 | 预估工作量 |
| --- | --- | --- | --- | --- |
| M1 | 6 | 1-2 个增量迁移 | 改造 3 页 + shared | 小 |
| M2 | ~20 | 2 新表 + 3-4 增量迁移 | 新增 5 页 + 小程序 | 大 |
| M3 | ~15 | 1-2 增量迁移（支付单） | admin 新增 5 页 | 大（含网关联调） |
| M4 | 少量 | 无 | 无 | 中 |

## 7. 确认后的执行方式

1. 按里程碑建分支（`feat/m1-auth-listing` 等），每个能力域独立 PR。
2. 每个里程碑结束输出交付说明：新增接口清单、迁移清单、测试证据（verify 输出）、文档更新。
3. 任何偏离本方案的变更（接口路径、表结构）需同步更新本文档与 use-cases.yaml 后再实现。
