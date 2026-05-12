# recycle-mall

C2B2C 二手商品循环交易平台复现版（第一阶段：主体框架 + 持久层落库）。

## 已落地主体能力

- 商品信息与用户输入的基础接入（回收单创建入口）
- 回收核心流程串联：图片审核 -> SN 解析 -> 自动估价 -> 生成回收单据
- 物流追踪号生成与状态查询（数据库读写）
- 积分规则计算与积分流水落库
- 核心业务表结构初始化（H2 内存库 / MySQL 配置）
- 外部 Provider 适配层（百度 AI 审核 / 物流平台，支持 mock/real 切换）
- 二销闭环流程（后台发布二销商品、C端下单、支付幂等、取消、退款、履约发货）
- 超时未支付自动关单 + 操作审计日志
- 二销下单并发防超卖（乐观锁）
- Freemarker 商品详情页模板渲染

## 技术栈

- Java 25
- Spring Boot 3.5.0
- Freemarker
- Spring Data JPA
- H2 / MySQL

## 技术版本与部署环境汇总

开发与运行时技术版本（以当前仓库配置为准）：

- 语言与构建
  - Java: `25`（`pom.xml` -> `java.version=25`）
  - Maven: 使用 Maven 构建（`spring-boot-maven-plugin`，建议 `3.9+`）
- 核心框架
  - Spring Boot: `3.5.0`（`spring-boot-starter-parent`）
  - Spring Web / Validation / Data JPA / Security / OAuth2 Resource Server: 跟随 Spring Boot `3.5.0` 版本管理
  - Spring Security OAuth2 JOSE: 跟随 Spring Boot `3.5.0` 版本管理
  - Freemarker: 跟随 Spring Boot `3.5.0` 版本管理
- 数据库与驱动
  - H2: runtime 依赖（版本由 Spring Boot `3.5.0` 管理）
  - MySQL Connector/J: runtime 依赖（版本由 Spring Boot `3.5.0` 管理）
- 鉴权与令牌
  - JWT: HS256（基于 `spring-security-oauth2-jose`）
  - Access Token / Refresh Token: 配置于 `security.auth.jwt.*`

部署环境汇总（当前工程）：

- 本地开发默认环境（`application.yml`）
  - Profile: 默认（不指定）
  - DB: H2 内存库（`jdbc:h2:mem:recycle_mall`）
  - 端口: `8080`
  - 特征: SQL 初始化开启（`spring.sql.init.mode=always`），适合快速联调
- 本地 MySQL 环境（`application-mysql.yml`）
  - Profile: `mysql`（启动参数 `-Dspring-boot.run.profiles=mysql`）
  - DB: MySQL（默认示例 `localhost:3306/recycle_mall`）
  - 特征: `ddl-auto=update`、`sql.init.mode=never`，适合近真实环境验证
- 测试环境（建议）
  - 建议基线: 基于 `mysql` profile 外置配置（数据库/密钥/回调地址）
  - 建议要求: 使用独立库与独立 JWT 密钥；关闭 demo 凭据
- 生产环境（建议）
  - 建议基线: 基于 MySQL 部署，所有密钥（JWT/支付回调）走安全配置中心或环境变量
  - 建议要求:
    - 替换 `security.auth.jwt.secret`
    - 替换 `payment.callback.secret`
    - 使用最小权限数据库账号
    - 固化审计与导出任务保留策略

## 快速启动

```bash
mvn spring-boot:run
```

启用 MySQL 配置启动：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## 接口示例

创建回收单：

```bash
curl -X POST http://localhost:8080/api/recycle/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId":1001,
    "snCode":"SN-123456",
    "imageUrl":"https://demo/image.jpg",
    "wearScore":85,
    "recycleCount":3
  }'
```

商品详情页：

```bash
http://localhost:8080/products/10001.html
```

查询物流状态：

```bash
curl "http://localhost:8080/api/recycle/logistics/status?trackingNo=TRK-RCY-XXXXXX"
```

后台查看回收单：

```bash
curl "http://localhost:8080/api/admin/recycle/orders"
```

后台审核回收单：

```bash
curl -X PATCH http://localhost:8080/api/admin/recycle/orders/review \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"RCY-ABCDEFGH",
    "action":"QUALITY_CHECK",
    "reviewedGrade":"GOOD"
  }'
```

审核动作状态流转：

- `QUALITY_CHECK`：`CREATED -> QUALITY_CHECKED`
- `PRICE_REVIEW`：`QUALITY_CHECKED -> PRICE_REVIEWED`
- `LIST_ON_SHELF`：`PRICE_REVIEWED -> LISTED`

发布二销商品（后台）：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/listings/publish \
  -H "Content-Type: application/json" \
  -d '{
    "recycleOrderNo":"RCY-ABCDEFGH",
    "salePrice":1699.00,
    "stock":1
  }'
```

获取C端在售商品：

```bash
curl "http://localhost:8080/api/mall/listings"
```

按等级/库存筛选并按价格降序：

```bash
curl "http://localhost:8080/api/mall/listings?grade=GOOD&minStock=1&sortBy=price&sortOrder=desc"
```

创建C端订单：

```bash
curl -X POST http://localhost:8080/api/mall/orders \
  -H "Content-Type: application/json" \
  -d '{
    "buyerUserId":1002,
    "listingId":1
  }'
```

查询订单履约轨迹：

```bash
curl "http://localhost:8080/api/mall/orders/B2C-ABCDEFGH/track?buyerUserId=1002"
```

返回中包含 `reviewEligibility`，可直接驱动前端按钮态：
- `hasReviewed`：是否已首评
- `canCreateReview`：当前是否可首评（需 `COMPLETED` 且未评价）
- `canAppendReview`：当前是否可追评（需窗口内且未追评）
- `appendWindowDays`：追评窗口配置
- `appendDeadline`：追评截止时间（无完成时间时为空）
- `appendRemainingHours` / `appendRemainingDays`：追评剩余时长（已过期时为 `0`）
- `appendRemainingText`：可直接展示的追评剩余文案（如“还可追评 2 天 5 小时”）
- `appendRemainingTextI18n`：多语言剩余文案（当前包含 `zh-CN`、`en-US`）

添加收藏：

```bash
curl -X POST http://localhost:8080/api/mall/favorites/add \
  -H "Content-Type: application/json" \
  -d '{
    "userId":1002,
    "listingId":1
  }'
```

取消收藏：

```bash
curl -X POST http://localhost:8080/api/mall/favorites/remove \
  -H "Content-Type: application/json" \
  -d '{
    "userId":1002,
    "listingId":1
  }'
```

查询我的收藏：

```bash
curl "http://localhost:8080/api/mall/favorites?userId=1002"
```

提交评价：

```bash
curl -X POST http://localhost:8080/api/mall/reviews/create \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "buyerUserId":1002,
    "rating":5,
    "content":"成色很好，和描述一致",
    "imageUrls":[
      "https://cdn.example.com/review/img1.jpg",
      "https://cdn.example.com/review/img2.jpg"
    ]
  }'
```

说明：仅 `COMPLETED`（已确认收货）订单允许评价；`DELIVERED/TO_DELIVER/WAIT_PAY` 等状态会被拒绝。
追评同样要求订单为 `COMPLETED`，且默认需在确认收货后 `30` 天窗口内完成（可通过 `mall.review.append-window-days` 配置）。

追评：

```bash
curl -X POST http://localhost:8080/api/mall/reviews/append \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "buyerUserId":1002,
    "appendContent":"用了两周依然稳定"
  }'
```

商家回复：

```bash
curl -X POST http://localhost:8080/api/mall/reviews/reply \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "merchantReply":"感谢反馈，欢迎再次选购",
    "operator":"admin"
  }'
```

查询商品评价：

```bash
curl "http://localhost:8080/api/mall/reviews?listingId=1&sortStrategy=SMART&includeHidden=false"
```

可选排序策略：`SMART`（默认）、`NEWEST`、`RATING`、`USEFUL`。
`includeHidden` 默认为 `false`，仅运营排查场景可传 `true` 查看已隐藏评价。

评价有用性投票：

```bash
curl -X POST http://localhost:8080/api/mall/reviews/vote-useful \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "voterUserId":1003
  }'
```

举报评价：

```bash
curl -X POST http://localhost:8080/api/mall/reviews/report \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "reporterUserId":1003,
    "reason":"疑似恶意差评"
  }'
```

管理端查看举报工单（可按状态筛选）：

```bash
curl "http://localhost:8080/api/admin/recycle/review-reports?status=PENDING"
```

管理端查看举报工单详情：

```bash
curl "http://localhost:8080/api/admin/recycle/review-reports/1"
```

管理端处理举报工单：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/review-reports/process \
  -H "Content-Type: application/json" \
  -d '{
    "reportId":1,
    "action":"APPROVE_HIDE",
    "processNote":"命中违规评价规范，已确认",
    "operator":"admin",
    "requestId":"req-review-process-001",
    "changeSummary":{"changedCount":1,"changedKeys":["status"]}
  }'
```

举报处置动作支持：`APPROVE`(等同降权)、`APPROVE_DEBOOST`、`APPROVE_HIDE`、`REJECT`。

管理端批量处理举报工单：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/review-reports/process-batch \
  -H "Content-Type: application/json" \
  -d '{
    "reportIds":[1,2,3],
    "action":"APPROVE_DEBOOST",
    "processNote":"批量巡检处置",
    "operator":"admin",
    "requestId":"req-review-batch-001",
    "changeSummary":{"changedCount":3,"changedKeys":["status"]}
  }'
```

两个接口同样支持可选审计字段 `requestId/changeSummary`，会写入审计日志详情便于追溯；若未传 `requestId`，服务端会自动生成 `req-` 前缀链路标识，格式为 `req-` + 32 位小写十六进制。

管理端评价风控概览：

```bash
curl "http://localhost:8080/api/admin/recycle/review-risk/summary?lookbackMinutes=60"
```

管理端评价风控时间线（分钟级）：

```bash
curl "http://localhost:8080/api/admin/recycle/review-risk/timeline?lookbackMinutes=60"
```

管理端高风险商品 TopN：

```bash
curl "http://localhost:8080/api/admin/recycle/review-risk/top-listings?lookbackMinutes=60&topN=10"
```

管理端查询当前评价策略：

```bash
curl "http://localhost:8080/api/admin/recycle/review-strategy"
```

管理端缓存协商公共字段（`review-strategy / error-codes / config-center`）：

- 请求头：`If-None-Match`
- 响应头：`ETag`、`Last-Modified`
- 命中缓存返回：`304 Not Modified`

示例：

```bash
curl "http://localhost:8080/api/admin/recycle/review-strategy" \
  -H 'If-None-Match: "your-etag-from-last-response"'
```

管理端热更新评价策略：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/review-strategy/update \
  -H "Content-Type: application/json" \
  -d '{
    "updates":{
      "appendWindowDays":45,
      "deboostPenalty":30,
      "highSensitiveRate":0.28,
      "includeHiddenDefault":false
    },
    "operator":"admin"
  }'
```

可选审计字段：
- `requestId`：链路追踪 ID（建议 UUID）
- `changeSummary`：字段级变更摘要（如 `changedCount/changedKeys`）

`review-strategy` 关键字段说明：

- `appendWindowDays`：追评窗口天数（仅 `COMPLETED` 订单生效，默认 `30`）
- `includeHiddenDefault`：评价列表默认是否包含隐藏评价
- `deboostPenalty`：智能排序对 `DEBOOST` 评价的降权分
- `highSensitiveRate/mediumSensitiveRate`：敏感评价比例风险阈值
- `highPendingReports/mediumPendingReports`：待处理举报量风险阈值
- `highTotalReports/mediumTotalReports`：总举报量风险阈值
- `groupMeta`：分组元数据数组（`groupKey/displayOrder/titleI18n/descriptionI18n`），可用于渲染配置分组卡片
- `fieldMeta`：字段元数据数组（包含 `displayOrder/groupKey/editable/key/type/uiComponentHint/unit/step/range/labelI18n/descriptionI18n/validationMessageI18n`），可用于前端自动分组渲染策略配置表单与校验提示

推荐的配置增量拉取顺序（以 `appendWindowDays` 更新为例）：

1. `POST /api/admin/recycle/review-strategy/update` 更新策略（如 `appendWindowDays`）
2. `GET /api/admin/recycle/config-center/modules` 获取最新模块摘要（`digest`）
3. 客户端携带本地 `digest` 调用 `POST /api/admin/recycle/config-center/module-diff`
4. 若 `changed` 中包含 `reviewStrategy`，再调用 `GET /api/admin/recycle/config-center/module/reviewStrategy`
5. 前端按新策略刷新追评入口与提示文案（例如“剩余可追评天数”）

管理端查询全局异常码字典：

```bash
curl "http://localhost:8080/api/admin/recycle/error-codes/global"
```

字典项包含多语言字段：
- `defaultMessageI18n`：默认错误文案（`zh-CN/en-US`）
- `recommendedActionI18n`：推荐处置建议（`zh-CN/en-US`）

全局异常码字典同样遵循“管理端缓存协商公共字段”：

```bash
curl "http://localhost:8080/api/admin/recycle/error-codes/global" \
  -H 'If-None-Match: "your-etag-from-last-response"'
```

管理端查询降级动作类型字典：

```bash
curl "http://localhost:8080/api/admin/recycle/degrade-actions/dictionary"
```

管理端查询告警降噪规则：

```bash
curl "http://localhost:8080/api/admin/recycle/alert-noise-rules"
```

`alert-noise-rules` 同样返回可视化渲染元数据：
- `groupMeta`：分组卡片（`groupKey/displayOrder/titleI18n/descriptionI18n`）
- `fieldMeta`：字段元数据（`key/type/uiComponentHint/options/labelI18n/validationMessageI18n`）

管理端热更新告警降噪规则：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/alert-noise-rules/update \
  -H "Content-Type: application/json" \
  -d '{
    "updates":{
      "allowlistKeys":["config.sync.consecutive_failures"],
      "denylistKeys":["config.sync.cache_miss"],
      "quietHoursStart":"00:00",
      "quietHoursEnd":"07:00",
      "quietHourPassLevels":["CRITICAL"]
    },
    "operator":"admin"
  }'
```

同样支持可选审计字段 `requestId/changeSummary`，服务端会写入审计日志详情用于追溯；若未传 `requestId`，服务端会自动生成 `req-` 前缀链路标识，格式为 `req-` + 32 位小写十六进制。

管理端一次拉取配置中心聚合包（评价策略 + 全局异常码）：

```bash
curl "http://localhost:8080/api/admin/recycle/config-center/bundle?clientVersion=1.0.0"
```

聚合包接口同样遵循“管理端缓存协商公共字段”：

```bash
curl "http://localhost:8080/api/admin/recycle/config-center/bundle" \
  -H 'If-None-Match: "your-etag-from-last-response"'
```

`bundle` 中 `bootstrapPlan` 已升级为版本化步骤数组（`version + steps[]`），每个步骤包含 `id/order/required/minClientVersion/action/degradeAction/compatible/filtered`，其中 `degradeAction` 为结构化对象（`type + params + message`），并结合 `clientVersion` 做兼容判断（支持 SemVer 预发布与构建元信息，例如 `1.0.0-rc.1`、`1.0.0+build.9`），用于指导控制台启动顺序与缓存回退策略（避免前端硬编码初始化流程）。

`compatibility.status` 为动态值：`STABLE / WARN / UNSUPPORTED`。当存在不兼容必需步骤时，`status=UNSUPPORTED` 且 `errorCode=CLIENT_VERSION_UNSUPPORTED`；仅可选步骤不兼容时返回 `WARN`（`errorCode=NONE`），可用于前端统一提示升级与降级策略。

`bundle` 中新增 `moduleIndex`（模块名 -> `version/updatedAt/digest`），前端可按模块摘要判断是否需要增量更新。

按模块单独拉取配置（增量更新）：

```bash
curl "http://localhost:8080/api/admin/recycle/config-center/modules"
curl "http://localhost:8080/api/admin/recycle/config-center/module/reviewStrategy?clientVersion=1.0.0"
curl "http://localhost:8080/api/admin/recycle/config-center/module/globalErrorCodes"
curl "http://localhost:8080/api/admin/recycle/config-center/module/degradeActionTypes"
curl "http://localhost:8080/api/admin/recycle/config-center/module/alertNoiseRules"
curl "http://localhost:8080/api/admin/recycle/config-center/module/bootstrapPlan?clientVersion=1.0.0"
```

模块接口同样遵循“管理端缓存协商公共字段”。

`config-center/modules` 返回的每个模块条目包含 `version/updatedAt/digest`，可直接用于“先比摘要、再按需拉模块详情”。

`config-center/module/reviewStrategy` 关键响应片段示例：

```json
{
  "success": true,
  "data": {
    "name": "reviewStrategy",
    "module": {
      "groupMeta": [
        {
          "displayOrder": 1,
          "groupKey": "review.behavior",
          "titleI18n": { "zh-CN": "评价行为策略", "en-US": "Review behavior strategy" },
          "descriptionI18n": { "zh-CN": "控制评价展示、追评窗口与排序惩罚", "en-US": "Controls review visibility, append window and ranking penalties" }
        }
      ],
      "fieldMeta": [
        {
          "displayOrder": 1,
          "groupKey": "review.behavior",
          "editable": true,
          "key": "appendWindowDays",
          "type": "number",
          "uiComponentHint": "number-input",
          "unit": "days",
          "step": 1,
          "range": { "min": 1, "max": 3650 },
          "labelI18n": { "zh-CN": "追评窗口天数", "en-US": "Append review window days" },
          "descriptionI18n": { "zh-CN": "确认收货后允许追评的最长天数", "en-US": "Maximum days allowed for append review after receipt" },
          "validationMessageI18n": { "zh-CN": "请输入 1-3650 的整数天数", "en-US": "Enter an integer between 1 and 3650" }
        }
      ]
    }
  }
}
```

`config-center/module/alertNoiseRules` 关键响应片段示例：

```json
{
  "success": true,
  "data": {
    "name": "alertNoiseRules",
    "module": {
      "rules": {
        "allowlistKeys": ["config.sync.consecutive_failures"],
        "denylistKeys": ["config.sync.cache_miss"],
        "quietHours": { "start": "00:00", "end": "07:00" },
        "quietHourPassLevels": ["CRITICAL"]
      },
      "groupMeta": [
        {
          "displayOrder": 1,
          "groupKey": "alert.routing",
          "titleI18n": { "zh-CN": "路由规则", "en-US": "Routing Rules" },
          "descriptionI18n": { "zh-CN": "控制白名单与黑名单", "en-US": "Control allowlist and denylist" }
        }
      ],
      "fieldMeta": [
        {
          "displayOrder": 1,
          "groupKey": "alert.routing",
          "editable": true,
          "key": "allowlistKeys",
          "type": "array",
          "uiComponentHint": "tags-input",
          "labelI18n": { "zh-CN": "白名单键", "en-US": "Allowlist Keys" },
          "validationMessageI18n": { "zh-CN": "请输入字符串数组", "en-US": "Please provide a string array" }
        },
        {
          "displayOrder": 5,
          "groupKey": "alert.quiet-hours",
          "editable": true,
          "key": "quietHourPassLevels",
          "type": "array",
          "uiComponentHint": "multi-select",
          "options": ["INFO", "WARN", "CRITICAL"],
          "labelI18n": { "zh-CN": "静默时段放行级别", "en-US": "Quiet-Hour Pass Levels" }
        }
      ]
    }
  }
}
```

前端渲染伪代码（按 `groupMeta + fieldMeta` 自动生成配置表单）：

```ts
type Lang = "zh-CN" | "en-US";

function renderReviewStrategyForm(module: any, lang: Lang) {
  const groups = [...module.groupMeta].sort((a, b) => a.displayOrder - b.displayOrder);
  const fields = [...module.fieldMeta].sort((a, b) => a.displayOrder - b.displayOrder);

  for (const group of groups) {
    renderGroupCard({
      key: group.groupKey,
      title: group.titleI18n[lang] ?? group.titleI18n["zh-CN"],
      description: group.descriptionI18n[lang] ?? group.descriptionI18n["zh-CN"]
    });

    const groupFields = fields.filter((f) => f.groupKey === group.groupKey);
    for (const field of groupFields) {
      const label = field.labelI18n[lang] ?? field.labelI18n["zh-CN"];
      const desc = field.descriptionI18n[lang] ?? field.descriptionI18n["zh-CN"];
      const validationText = field.validationMessageI18n[lang] ?? field.validationMessageI18n["zh-CN"];

      renderField({
        key: field.key,
        label,
        description: desc,
        component: field.uiComponentHint, // switch / number-input / decimal-input
        editable: field.editable,
        unit: field.unit,
        step: field.step,
        min: field.range?.min,
        max: field.range?.max,
        validationText
      });
    }
  }
}
```

统一模块渲染器接口约定（TypeScript）：

```ts
type Lang = "zh-CN" | "en-US";

type I18nText = Record<Lang, string>;

type ModuleGroupMeta = {
  displayOrder: number;
  groupKey: string;
  titleI18n: I18nText;
  descriptionI18n: I18nText;
};

type ModuleFieldMeta = {
  displayOrder: number;
  groupKey: string;
  editable: boolean;
  key: string;
  type: "boolean" | "number" | "string" | "array";
  uiComponentHint: string; // switch/number-input/decimal-input/tags-input/time-input/multi-select...
  unit?: string;
  step?: number;
  range?: { min?: number; max?: number };
  options?: string[];
  labelI18n: I18nText;
  descriptionI18n?: I18nText;
  validationMessageI18n?: I18nText;
};

type ConfigRenderableModule<TData = Record<string, any>> = {
  version: string;
  updatedAt: string;
  compatibility: {
    status: "STABLE" | "WARN" | "UNSUPPORTED";
    minimumSupportedVersion: string;
  };
  cacheHintSeconds: number;
  groupMeta: ModuleGroupMeta[];
  fieldMeta: ModuleFieldMeta[];
  // 模块核心数据，如 reviewStrategy 字段集合或 alertNoiseRules.rules
  data: TData;
};

type ReviewStrategyData = {
  appendWindowDays: number;
  includeHiddenDefault: boolean;
  deboostPenalty: number;
  highSensitiveRate: number;
  mediumSensitiveRate: number;
  highPendingReports: number;
  mediumPendingReports: number;
  highTotalReports: number;
  mediumTotalReports: number;
};

type AlertNoiseRulesData = {
  allowlistKeys: string[];
  denylistKeys: string[];
  quietHours: { start: string; end: string };
  quietHourPassLevels: Array<"INFO" | "WARN" | "CRITICAL">;
};
```

建议：前端只维护一个 `renderConfigModule(module)` 渲染入口，按 `groupMeta + fieldMeta` 渲染 UI，按 `data` 读写值。

字段变更补丁生成器（自动构造 `updates` 差量）：

```ts
type Primitive = string | number | boolean | null | undefined;

function deepEqual(a: any, b: any): boolean {
  if (a === b) return true;
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false;
    return a.every((item, i) => deepEqual(item, b[i]));
  }
  if (typeof a === "object" && typeof b === "object" && a && b) {
    const aKeys = Object.keys(a);
    const bKeys = Object.keys(b);
    if (aKeys.length !== bKeys.length) return false;
    return aKeys.every((k) => deepEqual(a[k], b[k]));
  }
  return false;
}

function normalizeValueByFieldMeta(value: any, field: ModuleFieldMeta) {
  // 数值型字段可按 step 做精度归一，避免 0.30000000004 这类噪声差异
  if (field.type === "number" && typeof value === "number" && typeof field.step === "number") {
    const precision = String(field.step).includes(".")
      ? String(field.step).split(".")[1].length
      : 0;
    return Number(value.toFixed(precision));
  }

  // tags-input/multi-select 场景：可按字典序排序后比较，避免仅顺序变化导致误判
  if (Array.isArray(value) && (field.uiComponentHint === "tags-input" || field.uiComponentHint === "multi-select")) {
    return [...value].sort();
  }

  return value;
}

function buildUpdatesPatch(
  initialData: Record<string, any>,
  currentData: Record<string, any>,
  fieldMeta: ModuleFieldMeta[]
) {
  const updates: Record<string, any> = {};

  for (const field of fieldMeta) {
    const key = field.key;
    const beforeRaw = initialData[key];
    const afterRaw = currentData[key];

    const before = normalizeValueByFieldMeta(beforeRaw, field);
    const after = normalizeValueByFieldMeta(afterRaw, field);

    if (!deepEqual(before, after)) {
      updates[key] = afterRaw; // 提交原始输入值，由后端做最终校验
    }
  }

  return updates;
}
```

提交示例：

```ts
const updates = buildUpdatesPatch(initialData, formData, module.fieldMeta);
if (Object.keys(updates).length > 0) {
  await api.post("/api/admin/recycle/review-strategy/update", {
    updates,
    operator: currentUser.username
  });
}
```

字段级脏标记与离开页面确认（推荐）：

```ts
type DirtyMap = Record<string, boolean>;

function buildDirtyMap(
  initialData: Record<string, any>,
  currentData: Record<string, any>,
  fieldMeta: ModuleFieldMeta[]
): DirtyMap {
  const dirty: DirtyMap = {};
  for (const field of fieldMeta) {
    const key = field.key;
    const before = normalizeValueByFieldMeta(initialData[key], field);
    const after = normalizeValueByFieldMeta(currentData[key], field);
    dirty[key] = !deepEqual(before, after);
  }
  return dirty;
}

function hasDirtyFields(dirtyMap: DirtyMap): boolean {
  return Object.values(dirtyMap).some(Boolean);
}

function registerBeforeUnloadGuard(getDirty: () => boolean) {
  const handler = (e: BeforeUnloadEvent) => {
    if (!getDirty()) return;
    e.preventDefault();
    e.returnValue = ""; // 浏览器通用触发方式
  };
  window.addEventListener("beforeunload", handler);
  return () => window.removeEventListener("beforeunload", handler);
}
```

路由切换二次确认伪代码：

```ts
async function onRouteLeave(to: string, getDirty: () => boolean) {
  if (!getDirty()) return true;
  const confirmed = await showConfirmDialog({
    title: "存在未保存修改",
    content: "当前配置有未保存变更，是否确认离开？"
  });
  return confirmed;
}
```

交互建议：
- 保存成功后重置 `initialData = currentData`，并清空 dirty 状态
- 在表单右上角展示“未保存变更”徽标（当 `hasDirtyFields=true`）
- 支持“仅提交 dirty 字段”与“放弃更改（恢复 initialData）”

并发编辑冲突处理（ETag/版本）建议：
- 页面加载时记录模块 `etag` 与 `initialData`
- 提交前先对比最新 `etag`（或先拉模块头信息）
- 若发现版本变化，进入冲突处理流程：提示用户“配置已被他人更新”
- 冲突处理支持三种策略：`merge`（推荐）、`overwrite`（强制覆盖）、`reload`（放弃本地修改）

冲突检测与处理伪代码：

```ts
type ConflictStrategy = "merge" | "overwrite" | "reload";

async function saveWithConflictHandling(params: {
  moduleName: string;
  initialData: Record<string, any>;
  currentData: Record<string, any>;
  fieldMeta: ModuleFieldMeta[];
  initialEtag: string | null;
}) {
  const { moduleName, initialData, currentData, fieldMeta, initialEtag } = params;
  const updates = buildUpdatesPatch(initialData, currentData, fieldMeta);
  if (Object.keys(updates).length === 0) return { ok: true, skipped: true };

  // 1) 读取服务端最新 etag + 数据
  const latestResp = await api.get(`/api/admin/recycle/config-center/module/${moduleName}`);
  const latestEtag = latestResp.headers.etag ?? null;
  const latestData = latestResp.data.module?.data ?? latestResp.data.module ?? {};

  // 2) 未冲突：直接提交
  if (!initialEtag || initialEtag === latestEtag) {
    return submitUpdates(moduleName, updates);
  }

  // 3) 冲突：让用户选策略
  const strategy: ConflictStrategy = await showConflictResolutionDialog({
    title: "检测到配置冲突",
    content: "配置已被他人更新，选择处理方式：合并、覆盖或重新加载。"
  });

  if (strategy === "reload") {
    return { ok: false, conflict: true, action: "reload" };
  }

  if (strategy === "overwrite") {
    // 强制覆盖：以当前表单值重新构建 updates
    const overwriteUpdates = buildUpdatesPatch(latestData, currentData, fieldMeta);
    return submitUpdates(moduleName, overwriteUpdates);
  }

  // strategy === "merge"：三路合并（base=initial, local=current, remote=latest）
  const merged = threeWayMerge(initialData, currentData, latestData, fieldMeta);
  if (merged.hasHardConflict) {
    // 对无法自动合并的字段要求用户逐项确认
    const resolvedData = await showFieldLevelConflictDialog(merged.conflicts);
    const mergedUpdates = buildUpdatesPatch(latestData, resolvedData, fieldMeta);
    return submitUpdates(moduleName, mergedUpdates);
  }

  const mergedUpdates = buildUpdatesPatch(latestData, merged.result, fieldMeta);
  return submitUpdates(moduleName, mergedUpdates);
}
```

三路合并建议规则：
- 若字段仅本地变更：取本地值
- 若字段仅远端变更：取远端值
- 若双方都变更且值不同：标记冲突（需用户确认）
- 数组字段（如 tags）可按集合并集策略，但需保留“可回退”入口

审计日志关联建议（可追溯到人和变更）：
- 每次提交附带 `operator`（操作人）
- 生成并透传 `requestId`（链路追踪 ID，建议 UUID）
- 构建 `changeSummary`（字段级 before/after 摘要）
- 在前端日志、网关日志、后端审计日志中统一记录 `requestId`

`changeSummary` 生成伪代码：

```ts
type ChangeItem = { key: string; before: any; after: any };

function buildChangeSummary(
  initialData: Record<string, any>,
  currentData: Record<string, any>,
  fieldMeta: ModuleFieldMeta[]
) {
  const changes: ChangeItem[] = [];
  for (const field of fieldMeta) {
    const key = field.key;
    const before = initialData[key];
    const after = currentData[key];
    if (!deepEqual(
      normalizeValueByFieldMeta(before, field),
      normalizeValueByFieldMeta(after, field)
    )) {
      changes.push({ key, before, after });
    }
  }
  return {
    changedCount: changes.length,
    changedKeys: changes.map((c) => c.key),
    changes
  };
}
```

提交上报伪代码（含审计字段）：

```ts
async function submitWithAudit(moduleApi: string, payloadUpdates: Record<string, any>, ctx: {
  operator: string;
  initialData: Record<string, any>;
  currentData: Record<string, any>;
  fieldMeta: ModuleFieldMeta[];
}) {
  const requestId = crypto.randomUUID();
  const changeSummary = buildChangeSummary(ctx.initialData, ctx.currentData, ctx.fieldMeta);

  await api.post(moduleApi, {
    updates: payloadUpdates,
    operator: ctx.operator,
    requestId,        // 若后端尚未接收可先放 header
    changeSummary     // 若后端尚未接收可先写本地埋点
  }, {
    headers: {
      "X-Request-Id": requestId
    }
  });

  console.info("config_update_submitted", {
    requestId,
    operator: ctx.operator,
    changedCount: changeSummary.changedCount,
    changedKeys: changeSummary.changedKeys
  });
}
```

建议：若后端暂未开放 `requestId/changeSummary` 请求体字段，可先通过请求头和埋点日志灰度接入，后续再升级为正式契约字段。

提交更新时建议仅发送变更字段（`updates` 部分量）：

```json
{
  "updates": {
    "appendWindowDays": 45,
    "highSensitiveRate": 0.30
  },
  "operator": "admin"
}
```

前端配置增量同步伪代码（`modules -> module-diff -> changed modules fetch`）：

```ts
async function syncConfigCenter(clientVersion: string) {
  // 1) 拉模块摘要（可带 If-None-Match）
  const modulesResp = await api.get("/api/admin/recycle/config-center/modules");
  const serverItems = modulesResp.data.items; // [{ name, digest, version, updatedAt }]

  // 2) 读取本地摘要并请求服务端做 diff
  const localDigests = loadLocalDigests(); // e.g. { reviewStrategy: "md5-x", globalErrorCodes: "md5-y" }
  const diffResp = await api.post("/api/admin/recycle/config-center/module-diff", {
    clientVersion,
    localDigests
  });
  const changed = diffResp.data.changed ?? [];

  // 3) 按需拉取变化模块（可并发，且每个模块支持 If-None-Match）
  for (const item of changed) {
    const moduleName = item.name;
    const etag = loadModuleEtag(moduleName);
    const moduleResp = await api.get(`/api/admin/recycle/config-center/module/${moduleName}`, {
      params: { clientVersion },
      headers: etag ? { "If-None-Match": etag } : undefined
    });

    // 304: 本地仍可用，跳过写入
    if (moduleResp.status === 304) continue;

    // 200: 更新本地缓存 + digest + etag
    saveModule(moduleName, moduleResp.data.module);
    saveModuleDigest(moduleName, item.digest);
    saveModuleEtag(moduleName, moduleResp.headers.etag);
  }

  // 4) 刷新内存态（如 reviewStrategy 表单配置、错误码映射）
  hydrateRuntimeConfigFromLocalCache();
}
```

推荐策略：
- 模块接口优先使用 `If-None-Match`，减少重复传输
- `module-diff` 返回 `cacheHit/requestHash` 可用于观测短窗口幂等命中
- 若网络异常，优先回退到最近一次本地缓存并展示降级提示
- 对 `reviewStrategy` 模块可按 `groupMeta + fieldMeta` 动态重建配置页

两种常见接入模式：

1) 启动同步模式（阻塞初始化）

```ts
async function bootstrapApp() {
  try {
    await syncConfigCenter("1.0.0");
  } catch (e) {
    // 启动阶段允许降级：使用本地缓存继续启动
    loadConfigFromLocalCache();
    showToast("配置中心不可用，已使用本地缓存配置");
  }
  startApplication();
}
```

2) 后台轮询模式（非阻塞刷新）

```ts
function startConfigPolling() {
  const intervalMs = 60_000;
  setInterval(async () => {
    try {
      await syncConfigCenter("1.0.0");
      // 按需热更新运行时能力
      refreshFeatureFlagsAndFormSchema();
    } catch (e) {
      // 轮询失败只记录，不打断主流程
      console.warn("config sync failed", e);
    }
  }, intervalMs);
}
```

3) 手动强制刷新模式（按钮触发）

```ts
let syncing = false;
let lastSyncAt = 0;

async function onClickForceSync() {
  const now = Date.now();
  // 简单防抖：2 秒内重复点击直接忽略
  if (now - lastSyncAt < 2000) return;
  lastSyncAt = now;

  // 并发锁：上一次还在同步时不重复发起
  if (syncing) {
    showToast("正在同步中，请稍候");
    return;
  }

  syncing = true;
  setSyncButtonLoading(true);
  try {
    await syncConfigCenter("1.0.0");
    refreshFeatureFlagsAndFormSchema();
    showToast("配置同步成功");
  } catch (e) {
    // 保留当前运行态，提示可稍后重试
    showToast("配置同步失败，已保留当前配置");
    console.error("force sync failed", e);
  } finally {
    syncing = false;
    setSyncButtonLoading(false);
  }
}
```

4) 同步状态面板模式（可观测性）

建议在前端维护如下状态：

```ts
type ConfigSyncState = {
  lastSyncAt: string | null;
  lastDurationMs: number | null;
  lastCacheHit: boolean | null;
  consecutiveFailures: number;
  lastErrorMessage: string | null;
  lastRequestHash: string | null;
  syncInProgress: boolean;
};
```

状态更新伪代码：

```ts
const syncState: ConfigSyncState = {
  lastSyncAt: null,
  lastDurationMs: null,
  lastCacheHit: null,
  consecutiveFailures: 0,
  lastErrorMessage: null,
  lastRequestHash: null,
  syncInProgress: false
};

async function syncWithMetrics(clientVersion: string) {
  const start = performance.now();
  syncState.syncInProgress = true;
  try {
    const diffResp = await api.post("/api/admin/recycle/config-center/module-diff", {
      clientVersion,
      localDigests: loadLocalDigests()
    });
    // 这里可继续执行 changed modules 拉取逻辑...
    syncState.lastSyncAt = new Date().toISOString();
    syncState.lastDurationMs = Math.round(performance.now() - start);
    syncState.lastCacheHit = Boolean(diffResp.data.cacheHit);
    syncState.lastRequestHash = diffResp.data.requestHash ?? null;
    syncState.consecutiveFailures = 0;
    syncState.lastErrorMessage = null;
  } catch (e: any) {
    syncState.lastSyncAt = new Date().toISOString();
    syncState.lastDurationMs = Math.round(performance.now() - start);
    syncState.consecutiveFailures += 1;
    syncState.lastErrorMessage = e?.message ?? "unknown error";
  } finally {
    syncState.syncInProgress = false;
    renderSyncStatusPanel(syncState);
  }
}
```

面板建议展示：
- 最近同步时间（`lastSyncAt`）
- 最近耗时（`lastDurationMs`）
- 最近是否命中缓存（`lastCacheHit`）
- 连续失败次数（`consecutiveFailures`）
- 最近错误信息（`lastErrorMessage`）
- 最近请求哈希（`lastRequestHash`，用于排查重复请求）

告警阈值建议（可按业务调整）：
- `consecutiveFailures >= 3`：触发“配置同步连续失败”告警
- `lastDurationMs > 2000`：触发“配置同步耗时过高”告警
- 连续 `10` 次 `lastCacheHit=false`：提示“缓存命中率异常偏低”
- `lastSyncAt` 距当前超过 `10` 分钟：提示“同步停滞”

告警规则伪代码：

```ts
function evaluateConfigSyncAlerts(
  state: ConfigSyncState,
  recentCacheHits: boolean[],
  nowMs: number
) {
  const alerts: string[] = [];

  if (state.consecutiveFailures >= 3) {
    alerts.push("配置同步连续失败，请检查网络/鉴权/服务端可用性");
  }

  if ((state.lastDurationMs ?? 0) > 2000) {
    alerts.push("配置同步耗时过高，建议排查接口性能与网络链路");
  }

  const latest10 = recentCacheHits.slice(-10);
  if (latest10.length === 10 && latest10.every((x) => x === false)) {
    alerts.push("最近 10 次同步均未命中缓存，建议检查 ETag/If-None-Match 使用");
  }

  if (state.lastSyncAt) {
    const staleMs = nowMs - new Date(state.lastSyncAt).getTime();
    if (staleMs > 10 * 60 * 1000) {
      alerts.push("配置同步已停滞超过 10 分钟");
    }
  }

  return alerts;
}
```

告警分级与抑制窗口建议：
- `INFO`：提示类，不影响主流程（如缓存命中偏低）
- `WARN`：可恢复异常（如单次耗时超阈值、短时同步停滞）
- `CRITICAL`：高风险异常（如连续失败 >= 3）
- 建议设置“同类告警 5 分钟抑制窗口”，避免重复刷屏

分级与抑制伪代码：

```ts
type AlertLevel = "INFO" | "WARN" | "CRITICAL";
type AlertEvent = { key: string; level: AlertLevel; message: string; ts: number };

const alertSilenceWindowMs = 5 * 60 * 1000;
const lastAlertAtByKey = new Map<string, number>();

function emitAlertWithDedup(event: AlertEvent) {
  const lastTs = lastAlertAtByKey.get(event.key) ?? 0;
  if (event.ts - lastTs < alertSilenceWindowMs) {
    return; // 命中抑制窗口，跳过重复告警
  }
  lastAlertAtByKey.set(event.key, event.ts);
  pushAlertToPanel(event); // 展示到面板
  pushAlertToTelemetry(event); // 上报监控系统
}

function buildLeveledAlerts(state: ConfigSyncState, nowMs: number): AlertEvent[] {
  const alerts: AlertEvent[] = [];
  if (state.consecutiveFailures >= 3) {
    alerts.push({
      key: "config.sync.consecutive_failures",
      level: "CRITICAL",
      message: "配置同步连续失败，请立即排查",
      ts: nowMs
    });
  }
  if ((state.lastDurationMs ?? 0) > 2000) {
    alerts.push({
      key: "config.sync.latency_high",
      level: "WARN",
      message: "配置同步耗时偏高",
      ts: nowMs
    });
  }
  if (state.lastCacheHit === false) {
    alerts.push({
      key: "config.sync.cache_miss",
      level: "INFO",
      message: "本次同步未命中缓存",
      ts: nowMs
    });
  }
  return alerts;
}
```

告警恢复（RECOVERY）建议：
- 当 `consecutiveFailures` 从 `>=3` 降为 `0` 时，发送 `consecutive_failures_recovered`
- 当 `lastDurationMs` 从 `>2000` 回落到 `<=1200` 时，发送 `latency_recovered`
- 恢复事件建议同样做短抑制（如 2 分钟），避免边界抖动重复触发

恢复事件伪代码：

```ts
type RecoveryState = {
  wasFailureCritical: boolean;
  wasLatencyHigh: boolean;
};

const recoverySilenceWindowMs = 2 * 60 * 1000;
const lastRecoveryAtByKey = new Map<string, number>();
const recoveryState: RecoveryState = {
  wasFailureCritical: false,
  wasLatencyHigh: false
};

function emitRecoveryWithDedup(key: string, message: string, nowMs: number) {
  const lastTs = lastRecoveryAtByKey.get(key) ?? 0;
  if (nowMs - lastTs < recoverySilenceWindowMs) return;
  lastRecoveryAtByKey.set(key, nowMs);
  pushAlertToPanel({ key, level: "INFO", message, ts: nowMs });
}

function evaluateRecoveryEvents(state: ConfigSyncState, nowMs: number) {
  // failure: CRITICAL -> RECOVERY
  const failureCriticalNow = state.consecutiveFailures >= 3;
  if (recoveryState.wasFailureCritical && !failureCriticalNow && state.consecutiveFailures === 0) {
    emitRecoveryWithDedup(
      "config.sync.consecutive_failures_recovered",
      "配置同步连续失败已恢复",
      nowMs
    );
  }
  recoveryState.wasFailureCritical = failureCriticalNow;

  // latency: WARN -> RECOVERY (带回滞避免抖动)
  const latencyHighNow = (state.lastDurationMs ?? 0) > 2000;
  const latencyRecoveredNow = (state.lastDurationMs ?? 0) <= 1200;
  if (recoveryState.wasLatencyHigh && latencyRecoveredNow) {
    emitRecoveryWithDedup(
      "config.sync.latency_recovered",
      "配置同步耗时已恢复正常",
      nowMs
    );
  }
  recoveryState.wasLatencyHigh = latencyHighNow;
}
```

告警路由策略建议：
- `INFO`：仅写入控制台/运维面板（不打扰值班）
- `WARN`：面板 + 团队 IM 群通知（如企业微信/Slack）
- `CRITICAL`：面板 + IM + 值班电话/短信升级
- `RECOVERY`：默认按 `INFO` 路由，并附带恢复耗时

告警路由伪代码：

```ts
type AlertLevel = "INFO" | "WARN" | "CRITICAL";
type AlertPayload = {
  key: string;
  level: AlertLevel;
  message: string;
  ts: number;
  extra?: Record<string, any>;
};

async function routeAlert(alert: AlertPayload) {
  // 1) 所有级别都入面板
  await pushAlertToPanel(alert);

  // 2) 分级通知
  if (alert.level === "INFO") {
    return;
  }

  if (alert.level === "WARN") {
    await notifyIM({
      title: "[WARN] Config Sync",
      content: alert.message,
      ts: alert.ts
    });
    return;
  }

  // CRITICAL
  await notifyIM({
    title: "[CRITICAL] Config Sync",
    content: alert.message,
    ts: alert.ts
  });
  await notifyOncall({
    channel: "phone_or_sms",
    title: "Config Sync Critical Alert",
    content: alert.message,
    ts: alert.ts
  });
}
```

值班升级建议（CRITICAL）：
- 首次触发立即通知值班人
- 5 分钟仍未恢复则升级到二线负责人
- 15 分钟仍未恢复触发应急群全员广播

告警收敛策略建议（避免通知风暴）：
- 同类告警按 `key + level` 聚合，不按每次请求单独通知
- 设置聚合窗口（如 60 秒），窗口内仅更新计数与受影响实例列表
- 窗口结束时发送 1 条聚合告警（包含 `count/instances/firstSeen/lastSeen`）
- 恢复后自动关闭对应聚合事件并发送 1 条恢复通知

告警收敛伪代码：

```ts
type AggregateAlert = {
  key: string;
  level: "INFO" | "WARN" | "CRITICAL";
  count: number;
  instances: Set<string>;
  firstSeenTs: number;
  lastSeenTs: number;
  timerId?: any;
};

const aggregateWindowMs = 60_000;
const aggregateMap = new Map<string, AggregateAlert>();

function onRawAlert(event: { key: string; level: "INFO" | "WARN" | "CRITICAL"; instanceId: string; ts: number }) {
  const aggregateKey = `${event.key}::${event.level}`;
  let agg = aggregateMap.get(aggregateKey);
  if (!agg) {
    agg = {
      key: event.key,
      level: event.level,
      count: 0,
      instances: new Set<string>(),
      firstSeenTs: event.ts,
      lastSeenTs: event.ts
    };
    aggregateMap.set(aggregateKey, agg);
    agg.timerId = setTimeout(() => flushAggregate(aggregateKey), aggregateWindowMs);
  }

  agg.count += 1;
  agg.instances.add(event.instanceId);
  agg.lastSeenTs = event.ts;
}

async function flushAggregate(aggregateKey: string) {
  const agg = aggregateMap.get(aggregateKey);
  if (!agg) return;

  await routeAlert({
    key: agg.key,
    level: agg.level,
    message: `[聚合告警] ${agg.key} 在窗口内触发 ${agg.count} 次，影响实例 ${[...agg.instances].join(",")}`,
    ts: agg.lastSeenTs,
    extra: {
      count: agg.count,
      instances: [...agg.instances],
      firstSeenTs: agg.firstSeenTs,
      lastSeenTs: agg.lastSeenTs
    }
  });

  aggregateMap.delete(aggregateKey);
}

async function onAlertRecovered(key: string, level: "INFO" | "WARN" | "CRITICAL", ts: number) {
  const aggregateKey = `${key}::${level}`;
  const agg = aggregateMap.get(aggregateKey);
  if (agg?.timerId) clearTimeout(agg.timerId);
  aggregateMap.delete(aggregateKey);
  await routeAlert({
    key: `${key}.recovered`,
    level: "INFO",
    message: `[恢复] ${key} 已恢复`,
    ts
  });
}
```

告警降噪白名单/黑名单建议：
- **白名单（allowlist）**：即使在静默时段也允许触发的关键告警（如 `CRITICAL`）
- **黑名单（denylist）**：明确屏蔽的低价值告警（如测试环境固定噪声）
- **环境规则**：`dev/staging` 默认降噪更激进，`prod` 仅屏蔽明确定义噪声
- **时间段规则**：可配置夜间静默窗口（例如 `00:00-07:00`）仅放行 `CRITICAL`
- **优先级顺序**：`denylist > allowlist > time-window > default`

降噪规则伪代码：

```ts
type Env = "dev" | "staging" | "prod";
type AlertLevel = "INFO" | "WARN" | "CRITICAL";

type NoiseRuleConfig = {
  allowlistKeys: string[];
  denylistKeys: string[];
  quietHours: { start: string; end: string }; // e.g. 00:00-07:00
  quietHourPassLevels: AlertLevel[]; // e.g. ["CRITICAL"]
};

function shouldRouteAlert(
  alert: { key: string; level: AlertLevel },
  env: Env,
  now: Date,
  config: NoiseRuleConfig
) {
  // 1) 黑名单优先：直接丢弃
  if (config.denylistKeys.includes(alert.key)) {
    return { pass: false, reason: "denylist" };
  }

  // 2) 白名单：无条件放行
  if (config.allowlistKeys.includes(alert.key)) {
    return { pass: true, reason: "allowlist" };
  }

  // 3) 非生产环境默认降噪：仅放行 WARN/CRITICAL
  if (env !== "prod" && alert.level === "INFO") {
    return { pass: false, reason: "non_prod_info_suppressed" };
  }

  // 4) 静默时间窗口控制
  if (inQuietHours(now, config.quietHours.start, config.quietHours.end)) {
    if (!config.quietHourPassLevels.includes(alert.level)) {
      return { pass: false, reason: "quiet_hours_suppressed" };
    }
  }

  // 5) 默认放行
  return { pass: true, reason: "default" };
}
```

动态规则热更新建议（配置中心下发）：
- 将告警降噪规则作为独立配置模块（如 `alertNoiseRules`）托管在配置中心
- 客户端持有 `activeVersion` 与 `lastKnownGoodVersion`
- 每次同步先“校验规则”再“原子切换生效”
- 新规则异常时自动回滚到 `lastKnownGoodVersion`

动态热更新伪代码：

```ts
type NoiseRulesBundle = {
  version: string;
  updatedAt: string;
  rules: NoiseRuleConfig;
};

let activeRules: NoiseRulesBundle | null = null;
let lastKnownGoodRules: NoiseRulesBundle | null = null;

async function refreshNoiseRulesFromConfigCenter() {
  // 1) 拉取规则模块（可配合 module-diff）
  const resp = await api.get("/api/admin/recycle/config-center/module/alertNoiseRules");
  const incoming: NoiseRulesBundle = resp.data.module;

  // 2) 版本未变化可跳过
  if (activeRules?.version === incoming.version) return;

  // 3) 规则校验（字段完整性、时间窗口格式、level 合法性等）
  const valid = validateNoiseRules(incoming.rules);
  if (!valid.ok) {
    console.error("invalid noise rules", valid.reason);
    // 回滚到最近可用版本
    if (lastKnownGoodRules) activeRules = lastKnownGoodRules;
    return;
  }

  // 4) 原子切换
  const previous = activeRules;
  activeRules = incoming;
  lastKnownGoodRules = incoming;

  // 5) 生效验证（可选：用样本告警跑一次路由）
  const smokeOk = smokeTestAlertRouting(activeRules.rules);
  if (!smokeOk) {
    activeRules = previous ?? lastKnownGoodRules;
    console.error("noise rules smoke test failed, rolled back");
    return;
  }

  console.info("noise rules updated", {
    from: previous?.version ?? "none",
    to: incoming.version
  });
}
```

回滚策略建议：
- 自动回滚：校验失败/冒烟失败即回滚
- 手动回滚：支持在控制台指定 `targetVersion` 触发回退
- 观测留痕：记录 `fromVersion/toVersion/result/operator` 到审计日志

服务端计算模块差异（无需前端自行对比）：

```bash
curl -X POST "http://localhost:8080/api/admin/recycle/config-center/module-diff" \
  -H "Content-Type: application/json" \
  -d '{
    "clientVersion":"1.0.0",
    "localDigests":{
      "reviewStrategy":"md5-a",
      "globalErrorCodes":"md5-b"
    }
  }'
```

`module-diff` 返回 `requestHash/cacheHit/generatedAt/cacheWindowSeconds`，用于观测是否命中短窗口幂等缓存。

返回结构包含：

- `version`：字典版本号（用于客户端缓存与灰度）
- `updatedAt`：服务端生成时间
- `compatibility`：兼容性声明（当前 `STABLE`）
- `cacheHintSeconds`：建议缓存秒数
- `items`：异常码条目列表

支付订单：

```bash
curl -X POST http://localhost:8080/api/mall/orders/pay \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "idempotencyKey":"PAY-20260428-0001",
    "timestamp":1714291200,
    "nonce":"nonce-0001",
    "signature":"hmac_sha256_hex"
  }'
```

支付网关回调（推荐生产接入）：

```bash
curl -X POST http://localhost:8080/api/payment/callback \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: 1714291200" \
  -H "X-Signature: hmac_sha256_hex" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "idempotencyKey":"PAY-20260428-0001",
    "payStatus":"SUCCESS",
    "nonce":"nonce-0001"
  }'
```

后台分页查询支付回调日志：

```bash
curl "http://localhost:8080/api/admin/payment/callback-logs?page=0&size=20"
```

按状态筛选回调日志：

```bash
curl "http://localhost:8080/api/admin/payment/callback-logs?page=0&size=20&callbackStatus=FAILED"
```

后台手工重放支付回调：

```bash
curl -X POST http://localhost:8080/api/admin/payment/callback-logs/replay \
  -H "Content-Type: application/json" \
  -d '{
    "callbackLogId":1
  }'
```

回调日志入队异步重放：

```bash
curl -X POST http://localhost:8080/api/admin/payment/callback-logs/replay/enqueue \
  -H "Content-Type: application/json" \
  -d '{
    "callbackLogId":1
  }'
```

手工触发消费重放队列（调试用）：

```bash
curl -X POST http://localhost:8080/api/admin/payment/callback-logs/replay/consume \
  -H "Content-Type: application/json" \
  -d '{
    "maxCount":20
  }'
```

分页查询重放任务：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks?page=0&size=20&status=PENDING"
```

查询重放队列摘要（运维看板）：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks/summary"
```

查询支付重放“查询类审计动作”清单：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks/query-audit-actions" \
  -H "X-Trace-Id: trace-ops-query-audit-actions-001"

# 英文文案
curl "http://localhost:8080/api/admin/payment/replay-tasks/query-audit-actions?lang=en-US" \
  -H "X-Trace-Id: trace-ops-query-audit-actions-002"

# 不支持语言（将回退到默认语言）
curl "http://localhost:8080/api/admin/payment/replay-tasks/query-audit-actions?lang=fr-FR" \
  -H "X-Trace-Id: trace-ops-query-audit-actions-003"
```

查询重放队列健康状态（告警视图）：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks/health" \
  -H "X-Trace-Id: trace-ops-health-001"
```

一键健康巡检与处置建议：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks/diagnosis" \
  -H "X-Trace-Id: trace-ops-diagnosis-001"
```

清理性能验收检查（PASS/WARN）：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks/cleanup-performance-check" \
  -H "X-Trace-Id: trace-ops-cleanup-check-001"
```

半自动处置（自动消费，可选自动重投 DEAD）：

```bash
curl -X POST http://localhost:8080/api/admin/payment/replay-tasks/auto-handle \
  -H "Content-Type: application/json" \
  -H "X-Operator: ops.leexd" \
  -H "X-Trace-Id: trace-20260428-0001" \
  -d '{
    "allowRequeueDead": false,
    "consumeMaxCount": 50,
    "requeueMaxCount": 50
  }'
```

分页查询 auto-handle 幂等记录：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks/auto-handle-idempotency?page=0&size=20&traceId=trace-20260428"
```

查询单条幂等记录详情（含完整响应）：

```bash
curl "http://localhost:8080/api/admin/payment/replay-tasks/auto-handle-idempotency/detail?traceId=trace-20260428-0001"
```

删除单条幂等记录（按 traceId）：

```bash
curl -X POST http://localhost:8080/api/admin/payment/replay-tasks/auto-handle-idempotency/delete \
  -H "Content-Type: application/json" \
  -d '{
    "traceId":"trace-20260428-0001"
  }'
```

批量删除历史幂等记录（按创建时间）：

```bash
curl -X POST http://localhost:8080/api/admin/payment/replay-tasks/auto-handle-idempotency/delete-before \
  -H "Content-Type: application/json" \
  -d '{
    "beforeTime":"2026-04-28T17:00:00"
  }'
```

手工触发一次自动清理：

```bash
curl -X POST http://localhost:8080/api/admin/payment/replay-tasks/auto-handle-idempotency/cleanup \
  -H "Content-Type: application/json" \
  -d '{
    "retainDays":7
  }'
```

单条重放任务再投递（DEAD/FAILED -> PENDING）：

```bash
curl -X POST http://localhost:8080/api/admin/payment/replay-tasks/requeue \
  -H "Content-Type: application/json" \
  -d '{
    "taskId":1
  }'
```

批量再投递 DEAD 任务：

```bash
curl -X POST http://localhost:8080/api/admin/payment/replay-tasks/requeue/dead \
  -H "Content-Type: application/json" \
  -d '{
    "maxCount":50
  }'
```

取消未支付订单（C端）：

```bash
curl -X POST http://localhost:8080/api/mall/orders/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH"
  }'
```

后台确认发货：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/resale-orders/deliver \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "requestId":"req-deliver-001",
    "changeSummary":{"changedCount":1,"changedKeys":["fulfillStatus"]}
  }'
```

买家确认收货（DELIVERED -> COMPLETED）：

```bash
curl -X POST http://localhost:8080/api/mall/orders/confirm-receipt \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "buyerUserId":2
  }'
```

查询买家订单列表（支持支付状态/履约状态过滤与数量限制）：

```bash
curl "http://localhost:8080/api/mall/orders?buyerUserId=2&payStatus=PAID&fulfillStatus=DELIVERED&sortBy=createdAt&sortOrder=desc&limit=20"
```

订单列表接口支持缓存协商，公共字段见下方“缓存协商公共字段”。

按金额排序查询买家订单列表（高金额优先）：

```bash
curl "http://localhost:8080/api/mall/orders?buyerUserId=2&payStatus=PAID&sortBy=amount&sortOrder=desc&limit=20"
```

排序参数约束：

- `sortBy` 仅支持 `createdAt`、`amount`
- `sortOrder` 仅支持 `asc`、`desc`

分页查询买家订单列表（支持 `page/size/hasMore`）：

```bash
curl "http://localhost:8080/api/mall/orders?buyerUserId=2&payStatus=PAID&sortBy=createdAt&sortOrder=asc&page=0&size=20"
```

订单列表项会附带展示态字段：`statusText`、`statusTextI18n`（`zh-CN`/`en-US`），用于前端直接渲染订单状态文案。

查询订单状态字典（前端可用于统一渲染状态标签）：

```bash
curl "http://localhost:8080/api/mall/orders/status-dictionary"
```

查询买家订单概览（个人中心统计）：

```bash
curl "http://localhost:8080/api/mall/orders/summary?buyerUserId=2"
```

按时间窗口查询订单概览（例如近 7 天）：

```bash
curl "http://localhost:8080/api/mall/orders/summary?buyerUserId=2&lookbackDays=7"
```

返回指标包含：`summaryScope(lookbackDays/fromTime/toTime)`、`totalOrders`、`payStatusCounts`、`fulfillStatusCounts`、`payStatusBreakdown`、`fulfillStatusBreakdown`、`totalAmount`、`paidAmount`、`refundedAmount`、`completedOrders`、`completionRate`、`refundRate`、`healthScore`、`healthLevel`、`healthLevelI18n`、`healthMeta`、`healthInsights`、`summaryMeta`、`comparison`。其中 breakdown 项包含 `status/count/ratio/amount/label/labelI18n`，`healthMeta` 包含评分公式、权重与等级阈值说明，`healthInsights` 提供可执行诊断建议（含中英文），`summaryMeta` 提供概览卡片与窗口切换配置（`cards` + `windowOptions`，含 `displayOrder/unit/componentHint/labelI18n/selected`），`comparison` 提供上一窗口指标、`delta/deltaI18n`、`trend/trendI18n`（`UP/DOWN/FLAT`）以及 `comparisonMeta`（指标单位/高低优先语义/趋势说明/`displayOrder`/`chartHint`）。

订单概览接口同样支持缓存协商，公共字段见下方“缓存协商公共字段”。

状态字典接口同样支持缓存协商，公共字段见下方“缓存协商公共字段”。

缓存协商公共字段（订单列表/状态字典/订单概览）：

- 请求头：`If-None-Match`
- 响应头：`ETag`、`X-Cache-Digest`（命中时返回 `304 Not Modified`）
- `200` 响应体：`data.cacheDigest`

后台退款（已支付订单）：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/resale-orders/refund \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo":"B2C-ABCDEFGH",
    "requestId":"req-refund-001",
    "changeSummary":{"changedCount":2,"changedKeys":["payStatus","fulfillStatus"]}
  }'
```

后台触发自动确认收货（仅处理已发货且超过阈值的订单）：

```bash
curl -X POST http://localhost:8080/api/admin/recycle/resale-orders/auto-confirm-receipt \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "confirmAfterMinutes":4320,
    "batchSize":200,
    "requestId":"req-auto-confirm-001",
    "changeSummary":{"changedCount":1,"changedKeys":["fulfillStatus"]}
  }'
```

以上三个管理端接口同样支持可选审计字段 `requestId/changeSummary`，会写入审计日志详情用于追溯；若未传 `requestId`，服务端会自动生成 `req-` 前缀链路标识，格式为 `req-` + 32 位小写十六进制。

查询审计日志（后台）：

```bash
curl "http://localhost:8080/api/admin/recycle/audit-logs?actionType=RESALE_ORDER_PAY&targetId=B2C-ABCDEFGH&limit=20"
```

分页查询审计日志（后台）：

```bash
curl "http://localhost:8080/api/admin/recycle/audit-logs/page?actionType=RESALE_ORDER_PAY&page=0&size=20"
```

按运维查询动作快速筛查（支付重放诊断/验收）：

```bash
# 健康查询审计
curl "http://localhost:8080/api/admin/recycle/audit-logs/page?actionType=PAYMENT_REPLAY_HEALTH_QUERY&page=0&size=20"

# 查询类审计动作清单查询审计
curl "http://localhost:8080/api/admin/recycle/audit-logs/page?actionType=PAYMENT_REPLAY_QUERY_AUDIT_ACTIONS_QUERY&page=0&size=20"

# 诊断查询审计
curl "http://localhost:8080/api/admin/recycle/audit-logs/page?actionType=PAYMENT_REPLAY_DIAGNOSIS_QUERY&page=0&size=20"

# 清理性能验收查询审计
curl "http://localhost:8080/api/admin/recycle/audit-logs/page?actionType=PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY&page=0&size=20"
```

导出审计日志 CSV（后台）：

```bash
curl -L "http://localhost:8080/api/admin/recycle/audit-logs/export?actionType=RESALE_ORDER_PAY&limit=1000" -o audit-logs.csv
```

## 二手评估算法实现细节

当前估价实现位于 `ValuationService`，采用“规则表匹配 + 兜底策略”的双层机制。

### 1) 输入因子

在创建回收单时，系统会收集并传入以下字段：

- `brand`：品牌（由 SN 解析得到）
- `model`：型号（由 SN 解析得到）
- `productionDate`：生产日期（由 SN 解析得到）
- `wearScore`：图片磨损分（0-100，越高表示成色越好）

并实时计算：

- `months`：机龄（月）= `ChronoUnit.MONTHS.between(productionDate, now)`

### 2) 规则表结构

规则存放在 `valuation_rule` 表，核心字段：

- `brand`、`model`：支持精确值或 `ALL` 通配
- `min_months`、`max_months`：机龄区间
- `min_wear_score`、`max_wear_score`：磨损分区间
- `grade`：输出品阶（`GOOD` / `MEDIUM` / `UNQUALIFIED`）
- `price`：输出估价

### 3) 匹配流程

服务查询规则时会取两组候选：

- 品牌：`[当前brand, ALL]`
- 型号：`[当前model, ALL]`

然后按如下条件过滤：

- `min_months <= months <= max_months`
- `min_wear_score <= wearScore <= max_wear_score`

### 4) 优先级策略

如果命中多条规则，会用“通配越少优先级越高”的策略选最终规则：

- 精确品牌 + 精确型号（优先级最高）
- 精确品牌 + `ALL` 型号
- `ALL` 品牌 + 精确型号
- `ALL` 品牌 + `ALL` 型号（兜底规则）

实现方式是计算 `rulePriority`：

- `brand == ALL` 记 1 分
- `model == ALL` 记 1 分
- 总分越低优先级越高

### 5) 兜底机制（保证可用）

当规则表没命中时，不会报错，而是按机龄走内置兜底：

- `months <= 18` => `GOOD`, 1800
- `18 < months <= 36` => `MEDIUM`, 1200
- `months > 36` => `UNQUALIFIED`, 300

### 6) 一个完整示例

输入：

- `brand=DEMO_BRAND`
- `model=DEMO_MODEL`
- `productionDate=当前时间-24个月`
- `wearScore=85`

过程：

1. 计算 `months=24`
2. 命中区间规则（19-36 个月、磨损分 60-100）
3. 输出 `grade=MEDIUM`
4. 输出 `price=1500.00`

### 7) 可扩展建议

- 在规则表增加 `channel`（回收渠道）、`region`（地区）、`category`（品类）字段做差异化定价
- 增加规则版本号与生效时间，支持灰度切换
- 把 `wearScore` 改为多维图像特征（划痕、磕碰、屏幕完好度）并通过加权计算

## 订单自动关单与审计

### 自动关单策略

- 定时任务：`ResaleOrderScheduler`
- 默认扫描周期：每 60 秒
- 默认过期阈值：创建后 15 分钟未支付
- 作用对象：`pay_status=UNPAID && fulfill_status=WAIT_PAY`
- 处理动作：
  - 订单状态更新为 `AUTO_CLOSED`
  - 回补商品库存并恢复 `ON_SHELF`

配置项：

```yaml
mall:
  order:
    auto-close-expire-minutes: 15
    auto-close-fixed-delay-ms: 60000
    auto-close-batch-size: 200
    auto-confirm-receipt-after-minutes: 4320
    auto-confirm-receipt-fixed-delay-ms: 60000
    auto-confirm-receipt-batch-size: 200
  review:
    append-window-days: 30
    visibility:
      include-hidden-default: false
    sort:
      deboost-penalty: 25
    risk:
      level-high-sensitive-rate: 0.25
      level-medium-sensitive-rate: 0.12
      level-high-pending-reports: 10
      level-medium-pending-reports: 5
      level-high-total-reports: 30
      level-medium-total-reports: 15
  config-center:
    module-diff-cache-window-seconds: 30
```

说明：`/api/admin/recycle/review-strategy/update` 会在运行时热更新上述策略（进程内生效，重启后回到配置文件值）。

批处理说明：

- 自动关单不是一次性拉全量，而是按 `auto-close-batch-size` 分批处理
- 每批处理完成后继续拉下一批，直到没有命中订单
- 该策略可降低大数据量下的单次事务压力
- 自动确认收货按 `auto-confirm-receipt-batch-size` 分批扫描 `PAID + DELIVERED` 订单，并以发货日志时间判断是否超过阈值

### 审计日志

所有关键动作会落库到 `operation_audit_log`：

- 回收单创建
- 回收审核状态流转
- 二销商品发布
- 二销下单
- 支付确认
- 取消未支付订单
- 已支付退款
- 发货
- 定时自动关单

审计表字段：

- `action_type`
- `target_type`
- `target_id`
- `detail`
- `created_at`

查询接口：

- `GET /api/admin/recycle/audit-logs`
- `GET /api/admin/recycle/audit-logs/page`
- `GET /api/admin/recycle/audit-logs/export`
- 支持可选参数：
  - `actionType`
  - `targetId`
  - `limit`（默认 50，最大 200）
  - 分页接口支持：`page`、`size`（`size` 最大 200）
  - 导出接口支持：`limit`（默认 1000，最大 5000）

## 二销并发防超卖

为防止同一商品在并发下单时出现库存超卖，`resale_listing` 使用了 JPA 乐观锁：

- 表字段：`version BIGINT`
- 实体字段：`@Version private Long version`
- 下单扣库存使用 `saveAndFlush`，若版本冲突会抛出并发异常
- 接口层返回友好错误：`下单冲突，请刷新后重试`

压测建议：

1. 准备一个 `stock=1` 的在售商品
2. 并发发起多次 `POST /api/mall/orders`
3. 预期结果：仅 1 单成功，其余请求收到冲突或库存不足错误

## 支付幂等保证

`POST /api/mall/orders/pay` 引入 `idempotencyKey`，用于防止支付回调重复提交：

- 同一 `idempotencyKey + orderNo` 重复请求，不会重复改支付状态
- 服务直接返回首次支付结果快照，并标记 `idempotentReplay=true`
- 如果同一 `idempotencyKey` 被用于不同 `orderNo`，会直接拒绝

落库表：`payment_idempotency`

- `idempotency_key`（唯一）
- `order_no`
- `pay_status_snapshot`
- `created_at`

## 支付回调签名校验

支付接口在幂等处理前，会先做 HMAC-SHA256 签名校验，避免伪造回调：

- 配置项：
  - `payment.callback.secret`
  - `payment.callback.max-skew-seconds`
- 参与签名字段（按顺序）：
  - `orderNo`
  - `idempotencyKey`
  - `timestamp`（秒级 Unix 时间戳）
  - `nonce`

签名原文：

`orderNo|idempotencyKey|timestamp|nonce`

签名算法：

- `HmacSHA256(secret, payload)`，输出十六进制字符串
- 请求体 `signature` 必须与服务端计算结果一致
- 同时会校验时间窗口：`abs(now - timestamp) <= max-skew-seconds`

正式网关回调接口：

- `POST /api/payment/callback`
- Header:
  - `X-Timestamp`
  - `X-Signature`
- Body:
  - `orderNo`
  - `idempotencyKey`
  - `payStatus`
  - `nonce`

后台运维接口：

- `GET /api/admin/payment/callback-logs`
- `POST /api/admin/payment/callback-logs/replay`
- `POST /api/admin/payment/callback-logs/replay/enqueue`
- `POST /api/admin/payment/callback-logs/replay/consume`
- `GET /api/admin/payment/replay-tasks`
- `GET /api/admin/payment/replay-tasks/summary`
- `GET /api/admin/payment/replay-tasks/query-audit-actions`
- `GET /api/admin/payment/replay-tasks/health`
- `GET /api/admin/payment/replay-tasks/diagnosis`
- `GET /api/admin/payment/replay-tasks/cleanup-performance-check`
- `POST /api/admin/payment/replay-tasks/auto-handle`
- `GET /api/admin/payment/replay-tasks/auto-handle-idempotency`
- `GET /api/admin/payment/replay-tasks/auto-handle-idempotency/detail`
- `POST /api/admin/payment/replay-tasks/auto-handle-idempotency/delete`
- `POST /api/admin/payment/replay-tasks/auto-handle-idempotency/delete-before`
- `POST /api/admin/payment/replay-tasks/auto-handle-idempotency/cleanup`
- `POST /api/admin/payment/replay-tasks/requeue`
- `POST /api/admin/payment/replay-tasks/requeue/dead`

回调返回：

- `response-mode=json` 时：
  - 成功：`{"code":"SUCCESS","message":"OK"}`
  - 非成功支付状态：`{"code":"IGNORED","message":"payStatus not success"}`
- `response-mode=plain` 时：
  - 成功：`success`（可配置）
  - 非成功支付状态：`ignored`（可配置）

### nonce 防重放

为防止同一签名包在时间窗口内重复提交，系统会记录已使用 nonce：

- nonce 首次使用：校验通过并写入 `payment_nonce`
- 相同 nonce 再次提交：直接拒绝（`nonce 重放`）
- 定时清理过期 nonce，避免存储膨胀

新增配置：

```yaml
payment:
  callback:
    nonce-cleanup-fixed-delay-ms: 300000
    response-mode: json
    response-success-text: success
    response-ignored-text: ignored
```

## 支付回调审计与重放

回调请求会写入 `payment_callback_log`，记录：

- 订单号、幂等键、payStatus、nonce、timestamp、signature
- 回调处理状态（`SUCCESS` / `IGNORED` / `FAILED`）
- 错误信息、返回体、回调来源、重放次数

回调日志支持 `callbackStatus` 筛选（例如 `FAILED`）。

当网关回调异常时，可按以下流程处理：

1. 在后台分页查询回调日志定位失败记录
2. 修复配置或数据问题
3. 调用重放接口按日志 ID 重放支付处理

也可选择“异步重放队列”：

1. 把失败回调日志加入重放队列
2. 由定时任务自动批量消费并重放
3. 在队列表查看任务状态（`PENDING/PROCESSING/SUCCESS/DEAD`）

队列去重约束：

- 同一个 `callbackLogId` 在 `PENDING/PROCESSING` 状态下只允许存在一条任务
- 如果重复入队或重复再投递，系统会返回已有任务（`deduplicated=true`）

队列消费配置：

```yaml
payment:
  callback:
    replay-consume-fixed-delay-ms: 30000
    replay-consume-batch-size: 20
    replay-max-retry: 3
    replay-backoff-base-seconds: 5
    replay-backoff-max-seconds: 300
    replay-health-pending-threshold: 100
    replay-health-dead-threshold: 10
    replay-health-oldest-pending-minutes-threshold: 30
    replay-health-cleanup-warn-lookback-minutes: 30
    replay-auto-handle-trace-idempotent-window-seconds: 30
    replay-auto-handle-idempotency-cleanup-fixed-delay-ms: 3600000
    replay-auto-handle-idempotency-retain-days: 7
```

重试策略：

- 每次消费失败会累计 `retryCount`
- 每次失败会根据 `retryCount` 计算 `nextRetryAt`（指数退避）
- 当 `retryCount >= replay-max-retry`，任务进入 `DEAD`，不再自动重试
- `DEAD` 任务可通过人工排查后重新入队对应回调日志
- 也可直接对任务执行“再投递”（单条或批量 DEAD），无需重新查回调日志

退避规则：

- 公式：`backoff = min(base * 2^(retry-1), max)`
- 默认：`base=5s`，`max=300s`
- 只有 `nextRetryAt <= now` 的 `PENDING` 任务会被消费

队列摘要字段说明（`GET /api/admin/payment/replay-tasks/summary`）：

- `pending`: 当前待处理任务总数
- `processing`: 正在处理中的任务数
- `success`: 已成功完成任务数
- `dead`: 已进入死信任务数
- `readyToConsume`: 当前可立即消费的任务数（`PENDING` 且 `nextRetryAt <= now`）

队列健康接口说明（`GET /api/admin/payment/replay-tasks/health`）：

- `healthSchemaVersion`: 健康响应协议版本（当前 `1.0.0`）
- `requestId`: 请求标识（优先回传 `X-Trace-Id`；未传则服务端自动生成，格式为 `req-` + 32 位小写十六进制）
- `generatedAt`: 健康结果生成时间
- `statusDictionaryVersion`: 状态字典版本（当前 `1.0.0`）
- `statusDictionary`: 状态字典（`OK/WARN` 对应中文语义）
- `overallStatus`: `OK` 或 `WARN`
- `alerts`: 告警明细（阈值未触发时为空）
- `metrics`: 当前指标快照（含 `pending/dead/readyToConsume/oldestPendingAgeMinutes` 等）
  - 包含 `recentCleanupSlowWarn/latestCleanupWarnAt`，用于观测近期是否出现慢清理
- `thresholds`: 当前生效阈值配置
- 会写运维审计日志：`actionType=PAYMENT_REPLAY_HEALTH_QUERY`（包含 `requestId/overallStatus` 与关键指标）

响应示例（完整）：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "healthSchemaVersion": "1.0.0",
    "requestId": "trace-ops-health-001",
    "generatedAt": "2026-04-28T18:20:00",
    "statusDictionaryVersion": "1.0.0",
    "statusDictionary": {
      "OK": "队列健康",
      "WARN": "队列存在告警"
    },
    "overallStatus": "WARN",
    "alerts": [
      "PENDING积压超过阈值: pending=128, threshold=100"
    ],
    "metrics": {
      "pending": 128,
      "processing": 3,
      "success": 520,
      "dead": 6,
      "readyToConsume": 40,
      "oldestPendingCreatedAt": "2026-04-28T17:20:00",
      "oldestPendingAgeMinutes": 53,
      "recentCleanupSlowWarn": false,
      "latestCleanupWarnAt": ""
    },
    "thresholds": {
      "pending": 100,
      "dead": 10,
      "oldestPendingAgeMinutes": 30,
      "cleanupWarnLookbackMinutes": 30
    }
  }
}
```

响应示例（`lang=en-US`）：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "queryAuditActionsSchemaVersion": "1.0.0",
    "requestId": "trace-ops-query-audit-actions-002",
    "generatedAt": "2026-04-28T18:35:00",
    "requestedLang": "en-US",
    "langFallbackApplied": false,
    "langFallbackReason": "NONE",
    "defaultLang": "zh-CN",
    "supportedLangs": ["zh-CN", "en-US"],
    "supportedLangFallbackReasons": ["NONE", "UNSUPPORTED_LANG"],
    "preferredReadPath": "meta",
    "compatibility": {
      "compatibilityVersion": "1.0.0",
      "migrationStatus": "STABLE",
      "supportedMigrationStatuses": ["STABLE", "DEPRECATING", "SUNSET"],
      "migrationStatusDictionary": {
        "STABLE": "Structure is stable, no migration required",
        "DEPRECATING": "Deprecation phase, migrate to new structure soon",
        "SUNSET": "Old structure is nearing sunset with short-term compatibility only"
      },
      "migrationHintKey": "PREFER_META",
      "topLevelFieldsRetained": true,
      "topLevelFieldsDeprecated": false,
      "topLevelDeprecatedFields": [],
      "topLevelDeprecationTargetDate": "",
      "migrationHintDictionaryVersion": "1.0.0",
      "migrationHintDictionary": {
        "PREFER_META": "new clients should read from meta first",
        "MIGRATE_SOON": "top-level fields are in deprecating phase, please migrate to meta as soon as possible",
        "META_ONLY": "top-level fields are nearing sunset, read from meta only"
      },
      "migrationHint": "new clients should read from meta first"
    },
    "dictionaryVersions": {
      "statusDictionaryVersion": "1.0.0",
      "descDictionaryVersion": "1.0.0",
      "langFallbackReasonDictionaryVersion": "1.0.0"
    },
    "meta": {
      "requestId": "trace-ops-query-audit-actions-002",
      "generatedAt": "2026-04-28T18:35:00",
      "requestedLang": "en-US",
      "lang": "en-US",
      "langFallbackApplied": false,
      "langFallbackReason": "NONE",
      "defaultLang": "zh-CN",
      "supportedLangs": ["zh-CN", "en-US"],
      "supportedLangFallbackReasons": ["NONE", "UNSUPPORTED_LANG"],
      "schemaVersion": "1.0.0",
      "dictionaryVersions": {
        "statusDictionaryVersion": "1.0.0",
        "descDictionaryVersion": "1.0.0",
        "langFallbackReasonDictionaryVersion": "1.0.0"
      },
      "summary": {
        "totalCount": 3,
        "activeCount": 3,
        "inactiveCount": 0,
        "activeRate": 100
      },
      "compatibility": {
        "compatibilityVersion": "1.0.0",
        "migrationStatus": "STABLE",
        "supportedMigrationStatuses": ["STABLE", "DEPRECATING", "SUNSET"],
        "migrationStatusDictionary": {
          "STABLE": "Structure is stable, no migration required",
          "DEPRECATING": "Deprecation phase, migrate to new structure soon",
          "SUNSET": "Old structure is nearing sunset with short-term compatibility only"
        },
        "migrationHintKey": "PREFER_META",
        "topLevelFieldsRetained": true,
        "topLevelFieldsDeprecated": false,
        "topLevelDeprecatedFields": [],
        "topLevelDeprecationTargetDate": "",
        "migrationHintDictionaryVersion": "1.0.0",
        "migrationHintDictionary": {
          "PREFER_META": "new clients should read from meta first",
          "MIGRATE_SOON": "top-level fields are in deprecating phase, please migrate to meta as soon as possible",
          "META_ONLY": "top-level fields are nearing sunset, read from meta only"
        },
        "migrationHint": "new clients should read from meta first"
      }
    },
    "statusDictionaryVersion": "1.0.0",
    "statusDictionary": {
      "ACTIVE": "Active action",
      "DEPRECATED": "Deprecated action",
      "EXTERNAL_ONLY": "External requests only"
    },
    "descDictionaryVersion": "1.0.0",
    "langFallbackReasonDictionaryVersion": "1.0.0",
    "langFallbackReasonDictionary": {
      "NONE": "No language fallback",
      "UNSUPPORTED_LANG": "Requested language is unsupported"
    },
    "lang": "en-US",
    "descDictionary": {
      "queryAudit.health": "Health query",
      "queryAudit.diagnosis": "Diagnosis query",
      "queryAudit.cleanupPerformanceCheck": "Cleanup performance check query"
    },
    "summary": {
      "totalCount": 3,
      "activeCount": 3,
      "inactiveCount": 0,
      "activeRate": 100
    },
    "actions": [
      {
        "actionType": "PAYMENT_REPLAY_HEALTH_QUERY",
        "descKey": "queryAudit.health",
        "description": "Health query",
        "status": "ACTIVE"
      },
      {
        "actionType": "PAYMENT_REPLAY_DIAGNOSIS_QUERY",
        "descKey": "queryAudit.diagnosis",
        "description": "Diagnosis query",
        "status": "ACTIVE"
      },
      {
        "actionType": "PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY",
        "descKey": "queryAudit.cleanupPerformanceCheck",
        "description": "Cleanup performance check query",
        "status": "ACTIVE"
      }
    ],
    "convention": "*_QUERY actions are logged only for externally triggered query requests, not internal flow calls",
    "conventionStatus": "EXTERNAL_ONLY"
  }
}
```

响应示例（`lang=fr-FR`，发生回退）：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "queryAuditActionsSchemaVersion": "1.0.0",
    "requestId": "trace-ops-query-audit-actions-003",
    "generatedAt": "2026-04-28T18:39:00",
    "requestedLang": "fr-FR",
    "langFallbackApplied": true,
    "langFallbackReason": "UNSUPPORTED_LANG",
    "defaultLang": "zh-CN",
    "supportedLangs": ["zh-CN", "en-US"],
    "supportedLangFallbackReasons": ["NONE", "UNSUPPORTED_LANG"],
    "preferredReadPath": "meta",
    "compatibility": {
      "compatibilityVersion": "1.0.0",
      "migrationStatus": "STABLE",
      "supportedMigrationStatuses": ["STABLE", "DEPRECATING", "SUNSET"],
      "migrationStatusDictionary": {
        "STABLE": "结构稳定，无需迁移",
        "DEPRECATING": "进入迁移期，建议尽快切换到新结构",
        "SUNSET": "旧结构即将下线，仅保留短期兼容"
      },
      "migrationHintKey": "PREFER_META",
      "topLevelFieldsRetained": true,
      "topLevelFieldsDeprecated": false,
      "topLevelDeprecatedFields": [],
      "topLevelDeprecationTargetDate": "",
      "migrationHintDictionaryVersion": "1.0.0",
      "migrationHintDictionary": {
        "PREFER_META": "新接入客户端建议优先读取 meta",
        "MIGRATE_SOON": "顶层字段进入迁移期，请尽快切换到 meta",
        "META_ONLY": "顶层字段即将下线，请仅使用 meta"
      },
      "migrationHint": "新接入客户端建议优先读取 meta"
    },
    "dictionaryVersions": {
      "statusDictionaryVersion": "1.0.0",
      "descDictionaryVersion": "1.0.0",
      "langFallbackReasonDictionaryVersion": "1.0.0"
    },
    "meta": {
      "requestId": "trace-ops-query-audit-actions-003",
      "generatedAt": "2026-04-28T18:39:00",
      "requestedLang": "fr-FR",
      "lang": "zh-CN",
      "langFallbackApplied": true,
      "langFallbackReason": "UNSUPPORTED_LANG",
      "defaultLang": "zh-CN",
      "supportedLangs": ["zh-CN", "en-US"],
      "supportedLangFallbackReasons": ["NONE", "UNSUPPORTED_LANG"],
      "schemaVersion": "1.0.0",
      "dictionaryVersions": {
        "statusDictionaryVersion": "1.0.0",
        "descDictionaryVersion": "1.0.0",
        "langFallbackReasonDictionaryVersion": "1.0.0"
      },
      "summary": {
        "totalCount": 3,
        "activeCount": 3,
        "inactiveCount": 0,
        "activeRate": 100
      },
      "compatibility": {
        "compatibilityVersion": "1.0.0",
        "migrationStatus": "STABLE",
        "supportedMigrationStatuses": ["STABLE", "DEPRECATING", "SUNSET"],
        "migrationStatusDictionary": {
          "STABLE": "结构稳定，无需迁移",
          "DEPRECATING": "进入迁移期，建议尽快切换到新结构",
          "SUNSET": "旧结构即将下线，仅保留短期兼容"
        },
        "migrationHintKey": "PREFER_META",
        "topLevelFieldsRetained": true,
        "topLevelFieldsDeprecated": false,
        "topLevelDeprecatedFields": [],
        "topLevelDeprecationTargetDate": "",
        "migrationHintDictionaryVersion": "1.0.0",
        "migrationHintDictionary": {
          "PREFER_META": "新接入客户端建议优先读取 meta",
          "MIGRATE_SOON": "顶层字段进入迁移期，请尽快切换到 meta",
          "META_ONLY": "顶层字段即将下线，请仅使用 meta"
        },
        "migrationHint": "新接入客户端建议优先读取 meta"
      }
    },
    "lang": "zh-CN"
  }
}
```

默认告警规则：

- `pending >= replay-health-pending-threshold` 触发积压告警
- `dead >= replay-health-dead-threshold` 触发死信告警
- `oldestPendingAgeMinutes >= replay-health-oldest-pending-minutes-threshold` 触发超时告警
- 最近 `replay-health-cleanup-warn-lookback-minutes` 分钟内出现 `PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN` 时触发慢清理告警

队列诊断接口说明（`GET /api/admin/payment/replay-tasks/diagnosis`）：

- `diagnosisSchemaVersion`: 诊断响应协议版本（当前 `1.0.0`）
- `requestId`: 请求标识（优先回传 `X-Trace-Id`；未传则服务端自动生成，格式为 `req-` + 32 位小写十六进制）
- `generatedAt`: 诊断结果生成时间
- `dependentSchemaVersions`: 依赖协议版本（当前包含 `healthSchemaVersion/cleanupPerformanceCheckSchemaVersion`）
- `dependentStatusDictionaryVersions`: 依赖状态字典版本（当前包含 `healthStatusDictionaryVersion/cleanupStatusDictionaryVersion`）
- `statusDictionaryVersion`: 状态字典版本（当前 `1.0.0`）
- `statusDictionary`: 状态字典（状态码到中文语义）
- `overallStatus` 为综合状态：队列健康 `OK` 且清理验收 `PASS` 时为 `OK`，否则为 `WARN`
- `statusBreakdown` 给出子状态：`queueHealth` 与 `cleanupPerformanceCheck`
- 返回健康状态与告警（同 `health`）
- 新增 `suggestedActions` 建议动作列表（按优先级）
- `suggestedActions` 包含 `actionId`（稳定动作 ID）与 `category`（动作分类），便于前端做稳定逻辑判断和分组展示
- 返回顺序稳定：先按 `priority` 升序，再按 `actionId` 升序
- 返回 `cleanupPerformanceCheck`（同 `cleanup-performance-check` 验收结构），可一次性查看清理性能状态
- 动态优先级：当 `cleanupPerformanceCheck=“WARN”` 时，会自动提升 `CHECK_CLEANUP_PERFORMANCE` 的建议优先级
- 会写运维审计日志：`actionType=PAYMENT_REPLAY_DIAGNOSIS_QUERY`（包含 `requestId/overallStatus` 等）
  - 仅人工/外部调用 `/diagnosis` 时写入；`auto-handle` 内部诊断不会额外写该查询日志
- 命名约定：`*_QUERY` 类型审计动作仅用于“外部查询请求”留痕，不用于内部流程调用

综合状态判定规则：

- `overallStatus=OK`：`statusBreakdown.queueHealth=OK` 且 `statusBreakdown.cleanupPerformanceCheck=PASS`
- 其他情况统一为 `overallStatus=WARN`

响应示例（节选，展示内嵌 `cleanupPerformanceCheck.summary`）：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "diagnosisSchemaVersion": "1.0.0",
    "requestId": "trace-ops-diagnosis-001",
    "generatedAt": "2026-04-28T18:13:00",
    "dependentSchemaVersions": {
      "healthSchemaVersion": "1.0.0",
      "cleanupPerformanceCheckSchemaVersion": "1.0.0"
    },
    "dependentStatusDictionaryVersions": {
      "healthStatusDictionaryVersion": "1.0.0",
      "cleanupStatusDictionaryVersion": "1.0.0"
    },
    "statusDictionaryVersion": "1.0.0",
    "statusDictionary": {
      "OK": "队列健康且清理验收通过",
      "WARN": "队列健康或清理验收存在告警"
    },
    "overallStatus": "WARN",
    "statusBreakdown": {
      "queueHealth": "OK",
      "cleanupPerformanceCheck": "WARN"
    },
    "cleanupPerformanceCheck": {
      "cleanupPerformanceCheckSchemaVersion": "1.0.0",
      "requestId": "trace-ops-diagnosis-001",
      "generatedAt": "2026-04-28T18:13:00",
      "overallStatus": "WARN",
      "summary": {
        "signalPassCount": 2,
        "signalTotalCount": 3,
        "signalPassRate": 67
      }
    }
  }
}
```
- 典型建议顺序：
  - 先消费可立即任务：`POST /api/admin/payment/callback-logs/replay/consume`
  - 再处理死信重投：`POST /api/admin/payment/replay-tasks/requeue/dead`
  - 最后排查失败回调：`GET /api/admin/payment/callback-logs?callbackStatus=FAILED`
  - 若命中慢清理告警：`CHECK_CLEANUP_PERFORMANCE`（建议先用较小 `retainDays` 手工复跑清理，再排查 DB 慢 SQL/索引）
    - 立即处置：小窗口复跑 cleanup，观察 `durationMs` 是否回落
    - 持续优化：排查慢 SQL/索引命中并优化删除批量策略
    - 验收信号：`durationMs` 回落、`slowWarnTriggered=false`、`metrics.recentCleanupSlowWarn` 在窗口后恢复为 `false`

`category` 枚举示例：

- `QUEUE_BACKLOG`：队列积压处置
- `DEAD_LETTER`：死信任务处置
- `FAILURE_ANALYSIS`：失败原因排查
- `CLEANUP_PERFORMANCE`：清理性能优化
- `NO_ACTION`：无需操作

`actionId` 枚举示例：

- `ACTION_CONSUME_QUEUE`
- `ACTION_REQUEUE_DEAD`
- `ACTION_CHECK_FAILED_CALLBACK_LOGS`
- `ACTION_CHECK_CLEANUP_PERFORMANCE`
- `ACTION_NO_ACTION`

清理性能验收接口（`GET /api/admin/payment/replay-tasks/cleanup-performance-check`）：

- `cleanupPerformanceCheckSchemaVersion`: 验收响应协议版本（当前 `1.0.0`）
- `requestId`: 请求标识（优先回传 `X-Trace-Id`；未传则服务端自动生成，格式为 `req-` + 32 位小写十六进制）
- `generatedAt`: 验收结果生成时间
- `statusDictionaryVersion`: 状态字典版本（当前 `1.0.0`）
- `statusDictionary`: 状态字典（状态码到中文语义）
- `overallStatus`: `PASS` 或 `WARN`
- `summary`: 信号汇总（`signalPassCount/signalTotalCount/signalPassRate`）
- `signals`: 验收信号逐项结果（是否有近期 cleanup、duration 是否回落、慢告警是否清除）
  - `name` 示例：`HAS_RECENT_CLEANUP_RUN`、`DURATION_RECOVERED`、`SLOW_WARN_CLEARED`
- `metrics`: 当前观测值（`latestCleanupAt/latestCleanupWarnAt/latestCleanupDurationMs/recentCleanupSlowWarn`）
- `thresholds`: 使用中的阈值（`cleanupWarnDurationMs/cleanupWarnLookbackMinutes`）
- `recommendations`: 下一步建议动作清单
- 会写运维审计日志：`actionType=PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY`（包含 `requestId/overallStatus` 等）

状态判定规则：

- `overallStatus=PASS`：同时满足
  - 观察窗口内存在至少一次 cleanup 执行
  - 最新 cleanup 的 `durationMs <= cleanupWarnDurationMs`
  - 观察窗口内不存在慢清理告警（`CLEANUP_WARN`）
- 其他情况为 `overallStatus=WARN`

响应示例（完整）：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "cleanupPerformanceCheckSchemaVersion": "1.0.0",
    "requestId": "trace-ops-cleanup-check-001",
    "generatedAt": "2026-04-28T18:13:00",
    "statusDictionaryVersion": "1.0.0",
    "statusDictionary": {
      "PASS": "清理性能验收通过",
      "WARN": "清理性能验收未通过"
    },
    "overallStatus": "WARN",
    "summary": {
      "signalPassCount": 2,
      "signalTotalCount": 3,
      "signalPassRate": 67
    },
    "signals": [
      {
        "name": "HAS_RECENT_CLEANUP_RUN",
        "pass": true,
        "expected": "观察窗口内至少有一次 cleanup 执行",
        "actual": "latestCleanupAt=2026-04-28T18:10:00"
      },
      {
        "name": "DURATION_RECOVERED",
        "pass": false,
        "expected": "latest durationMs <= warnThresholdMs",
        "actual": "latestDurationMs=6200,warnThresholdMs=5000"
      },
      {
        "name": "SLOW_WARN_CLEARED",
        "pass": true,
        "expected": "观察窗口内无 CLEANUP_WARN",
        "actual": "观察窗口内无慢清理告警"
      }
    ],
    "metrics": {
      "recentCleanupSlowWarn": false,
      "latestCleanupAt": "2026-04-28T18:10:00",
      "latestCleanupWarnAt": "",
      "latestCleanupDurationMs": 6200
    },
    "thresholds": {
      "cleanupWarnDurationMs": 5000,
      "cleanupWarnLookbackMinutes": 30
    },
    "recommendations": [
      "缩小 retainDays 后复跑 cleanup，观察 durationMs 是否回落"
    ]
  }
}
```

队列半自动处置接口（`POST /api/admin/payment/replay-tasks/auto-handle`）：

- 入参：
  - `allowRequeueDead`：是否允许自动重投 DEAD（默认 `false`）
  - `consumeMaxCount`：本次自动消费上限（默认 `50`，最大 `200`）
  - `requeueMaxCount`：本次自动重投 DEAD 上限（默认 `50`，最大 `200`）
- 请求头（可选）：
  - `X-Operator`：操作者标识（用于审计归因）
  - `X-Trace-Id`：链路追踪标识（用于跨系统排障）
- 防重复执行：
  - 同一个 `X-Trace-Id` 在短时间窗口内重复调用，会直接复用首次处置结果
  - 幂等记录落库到 `payment_replay_auto_handle_idempotency`，支持多实例部署
  - 返回 `idempotentReplay=true` 表示命中防抖复用
  - 窗口由 `replay-auto-handle-trace-idempotent-window-seconds` 控制（默认 30 秒）
- 行为：
  - 先读取当前诊断快照（`beforeDiagnosis`）
  - 再自动执行可处理动作（消费 / 可选 DEAD 重投）
  - 最后返回处置后健康状态（`afterHealth`）
- 审计：
  - 每次调用会写入操作审计日志
  - `actionType=PAYMENT_REPLAY_AUTO_HANDLE`
  - `detail` 中会记录 `operator` 与 `traceId`
  - 可通过 `GET /api/admin/recycle/audit-logs?actionType=PAYMENT_REPLAY_AUTO_HANDLE&limit=20` 查询

支付重放查询类审计动作清单（`*_QUERY`）：

- `PAYMENT_REPLAY_HEALTH_QUERY`：健康查询
- `PAYMENT_REPLAY_QUERY_AUDIT_ACTIONS_QUERY`：查询类审计动作清单查询
- `PAYMENT_REPLAY_DIAGNOSIS_QUERY`：诊断查询
- `PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY`：清理性能验收查询
- 查询约定：仅记录外部请求触发的查询，不记录内部流程调用

查询类审计动作清单接口（`GET /api/admin/payment/replay-tasks/query-audit-actions`）：

- 返回 `queryAuditActionsSchemaVersion`（当前 `1.0.0`）
- 返回 `requestId`（优先回传 `X-Trace-Id`；未传则服务端自动生成，格式为 `req-` + 32 位小写十六进制）
- 返回 `generatedAt`
- 支持 `lang` 参数（当前支持 `zh-CN/en-US`，默认 `zh-CN`）
- 返回 `requestedLang` 与 `langFallbackApplied`（是否发生语言回退）
- 返回 `langFallbackReason`（回退原因，未回退时为 `NONE`）
- 返回 `defaultLang` 与 `supportedLangs`（语言回退与能力声明）
- 返回 `supportedLangFallbackReasons`（支持的回退原因枚举）
- 返回 `preferredReadPath`（当前为 `meta`，建议客户端优先读取）
- 返回 `requestSpecVersion`（入参规范版本，当前 `1.0.0`）
- 返回 `requestSpec`（机器可读入参规范，含 Header/Query 参数定义）
- 返回 `compatibility`（兼容策略声明，说明顶层平铺字段保留状态）
- `meta` 内也包含 `compatibility`（与顶层一致）
- `migrationHint` 会随 `migrationStatus` 自动变化（`STABLE/DEPRECATING/SUNSET`），并随 `lang` 返回对应语言文案
- `compatibility` 内包含 `migrationHintKey` 与 `migrationHintDictionary`，可同时支持机器判断和文案渲染
- `migrationStatusDictionary` 也会随 `lang` 返回对应语言文案
- 返回 `dictionaryVersions`（字典版本聚合：`status/desc/langFallbackReason`）
- 返回 `meta`（聚合元信息，包含请求、语言、版本信息；便于统一读取）
- 兼容说明：当前仍保留顶层平铺字段，新接入建议优先使用 `meta`
- `meta` 内也包含 `summary`（与顶层 `summary` 一致）
- 返回 `statusDictionaryVersion` 与 `statusDictionary`（状态字典，随 `lang` 返回对应文案）
- 返回 `descDictionaryVersion` 与 `descDictionary`（描述字典，`descKey -> 默认中文`）
- 返回 `langFallbackReasonDictionaryVersion` 与 `langFallbackReasonDictionary`（回退原因字典）
- 返回 `summary`（`totalCount/activeCount/inactiveCount/activeRate`）
- 返回 `actions` 列表（`actionType/descKey/description/status`）
- 返回 `convention`（查询审计约定说明）与 `conventionStatus`
- 会写运维审计日志：`actionType=PAYMENT_REPLAY_QUERY_AUDIT_ACTIONS_QUERY`

响应示例（完整）：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "queryAuditActionsSchemaVersion": "1.0.0",
    "requestId": "trace-ops-query-audit-actions-001",
    "generatedAt": "2026-04-28T18:27:00",
    "requestedLang": "zh-CN",
    "langFallbackApplied": false,
    "langFallbackReason": "NONE",
    "defaultLang": "zh-CN",
    "supportedLangs": ["zh-CN", "en-US"],
    "supportedLangFallbackReasons": ["NONE", "UNSUPPORTED_LANG"],
    "preferredReadPath": "meta",
    "requestSpecVersion": "1.0.0",
    "requestSpec": {
      "headers": {
        "X-Trace-Id": {
          "required": false,
          "description": "链路追踪标识，未传时服务端自动生成 requestId（格式：req- + 32 位小写十六进制）"
        }
      },
      "query": {
        "lang": {
          "required": false,
          "default": "zh-CN",
          "supportedValues": ["zh-CN", "en-US"],
          "fallbackReasonIfUnsupported": "UNSUPPORTED_LANG"
        }
      }
    },
    "compatibility": {
      "compatibilityVersion": "1.0.0",
      "migrationStatus": "STABLE",
      "supportedMigrationStatuses": ["STABLE", "DEPRECATING", "SUNSET"],
      "migrationStatusDictionary": {
        "STABLE": "结构稳定，无需迁移",
        "DEPRECATING": "进入迁移期，建议尽快切换到新结构",
        "SUNSET": "旧结构即将下线，仅保留短期兼容"
      },
      "migrationHintKey": "PREFER_META",
      "topLevelFieldsRetained": true,
      "topLevelFieldsDeprecated": false,
      "topLevelDeprecatedFields": [],
      "topLevelDeprecationTargetDate": "",
      "migrationHint": "新接入客户端建议优先读取 meta",
      "migrationHintDictionaryVersion": "1.0.0",
      "migrationHintDictionary": {
        "PREFER_META": "新接入客户端建议优先读取 meta",
        "MIGRATE_SOON": "顶层字段进入迁移期，请尽快切换到 meta",
        "META_ONLY": "顶层字段即将下线，请仅使用 meta"
      }
    },
    "dictionaryVersions": {
      "statusDictionaryVersion": "1.0.0",
      "descDictionaryVersion": "1.0.0",
      "langFallbackReasonDictionaryVersion": "1.0.0"
    },
    "meta": {
      "requestId": "trace-ops-query-audit-actions-001",
      "generatedAt": "2026-04-28T18:27:00",
      "requestedLang": "zh-CN",
      "lang": "zh-CN",
      "langFallbackApplied": false,
      "langFallbackReason": "NONE",
      "defaultLang": "zh-CN",
      "supportedLangs": ["zh-CN", "en-US"],
      "supportedLangFallbackReasons": ["NONE", "UNSUPPORTED_LANG"],
      "schemaVersion": "1.0.0",
      "requestSpecVersion": "1.0.0",
      "dictionaryVersions": {
        "statusDictionaryVersion": "1.0.0",
        "descDictionaryVersion": "1.0.0",
        "langFallbackReasonDictionaryVersion": "1.0.0"
      },
      "summary": {
        "totalCount": 3,
        "activeCount": 3,
        "inactiveCount": 0,
        "activeRate": 100
      },
      "compatibility": {
        "compatibilityVersion": "1.0.0",
        "migrationStatus": "STABLE",
        "supportedMigrationStatuses": ["STABLE", "DEPRECATING", "SUNSET"],
        "migrationStatusDictionary": {
          "STABLE": "结构稳定，无需迁移",
          "DEPRECATING": "进入迁移期，建议尽快切换到新结构",
          "SUNSET": "旧结构即将下线，仅保留短期兼容"
        },
        "migrationHintKey": "PREFER_META",
        "topLevelFieldsRetained": true,
        "topLevelFieldsDeprecated": false,
        "topLevelDeprecatedFields": [],
        "topLevelDeprecationTargetDate": "",
        "migrationHintDictionaryVersion": "1.0.0",
        "migrationHintDictionary": {
          "PREFER_META": "新接入客户端建议优先读取 meta",
          "MIGRATE_SOON": "顶层字段进入迁移期，请尽快切换到 meta",
          "META_ONLY": "顶层字段即将下线，请仅使用 meta"
        },
        "migrationHint": "新接入客户端建议优先读取 meta"
      }
    },
    "lang": "zh-CN",
    "statusDictionaryVersion": "1.0.0",
    "statusDictionary": {
      "ACTIVE": "当前生效动作",
      "DEPRECATED": "已废弃动作",
      "EXTERNAL_ONLY": "仅外部请求触发记录"
    },
    "descDictionaryVersion": "1.0.0",
    "langFallbackReasonDictionaryVersion": "1.0.0",
    "langFallbackReasonDictionary": {
      "NONE": "未发生语言回退",
      "UNSUPPORTED_LANG": "请求语言不受支持，已回退到默认语言"
    },
    "descDictionary": {
      "queryAudit.health": "健康查询",
      "queryAudit.diagnosis": "诊断查询",
      "queryAudit.cleanupPerformanceCheck": "清理性能验收查询"
    },
    "summary": {
      "totalCount": 3,
      "activeCount": 3,
      "inactiveCount": 0,
      "activeRate": 100
    },
    "actions": [
      {
        "actionType": "PAYMENT_REPLAY_HEALTH_QUERY",
        "descKey": "queryAudit.health",
        "description": "健康查询",
        "status": "ACTIVE"
      },
      {
        "actionType": "PAYMENT_REPLAY_DIAGNOSIS_QUERY",
        "descKey": "queryAudit.diagnosis",
        "description": "诊断查询",
        "status": "ACTIVE"
      },
      {
        "actionType": "PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY",
        "descKey": "queryAudit.cleanupPerformanceCheck",
        "description": "清理性能验收查询",
        "status": "ACTIVE"
      }
    ],
    "convention": "*_QUERY 仅记录外部请求触发的查询，不记录内部流程调用",
    "conventionStatus": "EXTERNAL_ONLY"
  }
}
```

幂等记录查询接口（`GET /api/admin/payment/replay-tasks/auto-handle-idempotency`）：

- 支持参数：
  - `page`、`size`
  - `traceId`（模糊匹配）
  - `startAt`、`endAt`（ISO 时间，例如 `2026-04-28T17:00:00`）
- 返回字段：
  - `id`、`traceId`、`createdAt`、`expireAt`

幂等记录详情接口（`GET /api/admin/payment/replay-tasks/auto-handle-idempotency/detail`）：

- 参数：
  - `traceId`（精确匹配）
- 返回字段：
  - `id`、`traceId`、`createdAt`、`expireAt`、`expired`
  - `response`（当次 `auto-handle` 的完整响应快照）

幂等记录清理接口：

- 单条删除（`POST /api/admin/payment/replay-tasks/auto-handle-idempotency/delete`）
  - 入参：`traceId`
  - 返回：`deleted`（0 或 1）
- 批量删除（`POST /api/admin/payment/replay-tasks/auto-handle-idempotency/delete-before`）
  - 入参：`beforeTime`（ISO 时间）
  - 返回：`deleted`（删除条数）
- 手工触发清理（`POST /api/admin/payment/replay-tasks/auto-handle-idempotency/cleanup`）
  - 入参：`retainDays`（可选，默认 7）
  - 返回：`expiredDeleted/historyDeleted/totalDeleted/durationMs/warnThresholdMs/slowWarnTriggered`

自动清理任务：

- 定时任务：`PaymentReplayAutoHandleIdempotencyCleanupScheduler`
- 清理策略：
  - 删除已过期记录（`expireAt < now`）
  - 删除超出保留天数的历史记录（`createdAt < now - retainDays`）
- 配置项：
  - `payment.callback.replay-auto-handle-idempotency-cleanup-fixed-delay-ms`
  - `payment.callback.replay-auto-handle-idempotency-retain-days`
  - `payment.callback.replay-auto-handle-idempotency-cleanup-warn-duration-ms`
- 审计日志：
  - `actionType=PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP`
  - `detail` 包含 `retainDays/expiredDeleted/historyDeleted/totalDeleted/durationMs`
  - 当 `durationMs` 超过阈值时，额外记录 `actionType=PAYMENT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN`
  - `CLEANUP_WARN` 的 `detail` 包含 `durationMs/warnThresholdMs/retainDays/totalDeleted`
- 并发保护：
  - 手工触发与定时任务共享同一把互斥锁
  - 若已有清理在执行，后续请求返回 `skipped=true`
- 性能指标：
  - 清理结果会返回 `durationMs`
  - 清理结果会返回 `warnThresholdMs` 与 `slowWarnTriggered`
  - 并发跳过时 `durationMs=0`

## 外部平台接入配置

默认使用 mock：

```yaml
provider:
  image-audit:
    mode: mock
  logistics:
    mode: mock
```

切换真实对接：

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

说明：

- 图像审核 Provider 目前约定返回 JSON 字段 `pass`（boolean）
- 物流 Provider 目前约定返回 JSON 字段 `status`（string）
- 物流查询接口会同步调用外部平台并回写本地 `logistics_track` 最新状态

## 测试说明（query-audit-actions）

新增单元测试文件：

- `src/test/java/com/recycle/mall/application/RecycleApplicationServiceQueryAuditActionsTest.java`

覆盖点：

- `lang=zh-CN`：返回中文文案，且 `langFallbackApplied=false`
- `lang=en-US`：返回英文文案，且 `langFallbackApplied=false`
- `lang=fr-FR`：回退到默认语言 `zh-CN`，且 `langFallbackApplied=true`、`langFallbackReason=UNSUPPORTED_LANG`
- 结构一致性：校验顶层与 `meta` 的 `summary/compatibility/dictionaryVersions` 一致
- 协议一致性：校验 `requestSpecVersion` 与顶层/`meta` 的 `requestSpec` 一致
- 国际化一致性：校验 `migrationStatusDictionary/migrationHint` 在 `en-US` 与回退场景下的语言输出
- 契约稳定性：快照式校验关键字段存在与类型（`meta/summary/compatibility/requestSpec/dictionaries/actions`）
- 聚合一致性：校验 `summary(total/active/inactive/activeRate)` 与 `actions` 实际内容一致
- 字典完备性：校验 `actions.descKey` 全量命中 `descDictionary`，以及 `langFallbackReason` 与回退原因字典一致
- 兼容策略完备性：校验 `migrationStatus/migrationHintKey` 与对应字典及枚举声明一致
- 镜像一致性：校验顶层与 `meta` 的关键镜像字段（含 `generatedAt`、语言/回退/版本/入参规范）保持一致
- 读取路径一致性：当 `preferredReadPath=meta` 时，校验顶层与 `meta` 的 `requestSpec/compatibility` 一致
- 入参规范内容一致性：校验 `requestSpec` 中 `X-Trace-Id/lang` 的必填、默认值、支持值与回退原因声明
- 规范引用一致性：校验 `supportedLangs` 与 `requestSpec.query.lang.supportedValues` 一致，且回退原因声明可闭环校验

执行命令：

```bash
# 仅执行 query-audit-actions 契约测试
mvn -q -Dtest=RecycleApplicationServiceQueryAuditActionsTest test

# 执行 application 层全部测试（如果后续新增同目录测试）
mvn -q -Dtest='com.recycle.mall.application.*Test' test

# 执行全量测试
mvn -q test
```

执行前置检查：

```bash
java -version
mvn -version
```

常见问题：

- 如果提示 `mvn: command not found`，请先安装 Maven 并确认已加入 `PATH`
- 如果 Java 版本低于 17，请切换到 JDK 17 后再执行测试

测试结果判定：

- 成功：命令退出码为 `0`，并看到 `BUILD SUCCESS`
- 失败：命令退出码非 `0`，可优先查看 `Failures` 与 `Errors` 统计
- 单测定位：根据失败栈中的方法名（如 `shouldFallbackToDefaultLang...`）直接回看对应断言

一键回归脚本（可复制执行）：

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "[1/3] check java"
java -version

echo "[2/3] check maven"
mvn -version

echo "[3/3] run query-audit-actions contract tests"
mvn -q -Dtest=RecycleApplicationServiceQueryAuditActionsTest test

echo "done: query-audit-actions contract tests passed"
```

也可直接执行仓库脚本：

```bash
# query-audit-actions 契约测试
./scripts/run-query-audit-actions-tests.sh

# application 层回归
./scripts/run-application-tests.sh

# 全量回归
./scripts/run-all-tests.sh

# 统一入口（query|app|all）
./scripts/run-tests.sh query
./scripts/run-tests.sh app
./scripts/run-tests.sh all

# 列出可用测试范围（便于脚本探测）
./scripts/run-tests.sh list

# 机器可读 JSON 输出
./scripts/run-tests.sh list --json

# 说明：--json 仅支持 list 范围

# 预检脚本完整性与执行权限
./scripts/run-tests.sh self-check

# 预检结果 JSON 输出（便于 CI 解析）
./scripts/run-tests.sh self-check --json

# JSON 输出结构示例：
# list --json       => {"schemaVersion":"1.0.0","command":"list","requestedArgs":["list","--json"],"ok":true,"exitCode":0,"generatedAt":"2026-04-28T12:00:00Z","durationMs":5,"scopes":["query","app","all"]}
# self-check --json => {"schemaVersion":"1.0.0","command":"self-check","requestedArgs":["self-check","--json"],"ok":true,"exitCode":0,"generatedAt":"2026-04-28T12:00:00Z","durationMs":12,"results":[{"script":"...","ok":true}]}
# 备注：durationMs 使用跨平台毫秒时间实现（兼容 macOS/Linux）

CI 接入示例（bash）：

```bash
# 1) 探测可用范围
SCOPES_JSON="$(./scripts/run-tests.sh list --json)"
echo "$SCOPES_JSON"

# 2) 预检脚本完整性
SELF_CHECK_JSON="$(./scripts/run-tests.sh self-check --json)" || {
  echo "$SELF_CHECK_JSON"
  exit 1
}
echo "$SELF_CHECK_JSON"

# 3) 执行指定范围测试（示例：query）
./scripts/run-tests.sh query
```

CI 接入示例（bash + python3 解析 JSON）：

```bash
SCOPES_JSON="$(./scripts/run-tests.sh list --json)"
python3 - <<'PY' "$SCOPES_JSON"
import json, sys
data = json.loads(sys.argv[1])
assert data["ok"] is True
assert "query" in data["scopes"]
print("scopes check passed")
PY

SELF_CHECK_JSON="$(./scripts/run-tests.sh self-check --json)" || {
  echo "$SELF_CHECK_JSON"
  exit 1
}
python3 - <<'PY' "$SELF_CHECK_JSON"
import json, sys
data = json.loads(sys.argv[1])
assert data["ok"] is True
assert data["exitCode"] == 0
print("self-check passed")
PY
```

CI 入口脚本（推荐）：

```bash
# 默认执行 query（先 self-check，再执行测试）
./scripts/run-tests-ci.sh

# 指定范围
./scripts/run-tests-ci.sh app
./scripts/run-tests-ci.sh all

# 预演执行步骤（不实际执行）
./scripts/run-tests-ci.sh query --dry-run

# 机器可读输出（适合 CI 平台采集）
./scripts/run-tests-ci.sh query --json

# 校验 CI JSON 输出契约（参数错误/干跑场景）
./scripts/verify-run-tests-ci-json.sh

# 查看帮助
./scripts/run-tests-ci.sh --help
```

# 预览将执行的命令（不实际运行）
./scripts/run-tests.sh query --dry-run
./scripts/run-tests.sh --dry-run app

# 查看统一入口帮助
./scripts/run-tests.sh --help
```

## 下一阶段建议（可继续由我直接实现）

1. 接入真实支付网关 SDK（统一下单、验签、公钥轮换、证书管理），替代 mock 回调链路。
2. 增加支付与重放监控指标（成功率、死信增长率、平均重放时延）并接告警。
3. 完善 C 端二销评价能力（图片评价、评价举报、评价有用性投票）。
4. 增加关键链路集成测试（回调签名、nonce 重放、退避重试、死信再投递）。

## 登录与 RBAC 权限控制

当前项目已切换为 **Spring Security + OAuth2 Resource Server + JWT** 的登录与鉴权方案。

### 1) 依赖组件

- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server`
- `spring-security-oauth2-jose`

### 2) 认证与鉴权实现细节

核心文件：`src/main/java/com/recycle/mall/interfaces/config/SecurityConfig.java`

- 认证入口：`POST /api/auth/login`
  - 使用 `AuthenticationManager` + `DaoAuthenticationProvider` 做用户名密码认证
  - 用户来源：`UserAccountRepository.findByUsername(...)`
  - 密码校验：`PasswordEncoder`（当前演示账号使用 `{noop}` 前缀）
- JWT 签发：
  - 使用 `JwtEncoder(NimbusJwtEncoder)` 基于 HS256 对称密钥签发
  - 关键 claims：
    - `sub`: 用户名
    - `jti`: 访问令牌唯一 ID（用于黑名单注销）
    - `role`: 角色（如 `ADMIN` / `USER`）
    - `scope`: 业务 scope（示例：`mall recycle admin`）
    - `exp`: 过期时间
- JWT 校验：
  - 使用 `JwtDecoder(NimbusJwtDecoder)` 校验签名与有效期
  - `JwtAuthenticationConverter` 将 `role` claim 转换为 `ROLE_*` 权限
  - 额外校验 `jti` 是否命中 `auth_token_blacklist`，命中即拒绝访问
- Refresh Token：
  - 登录时同时签发 `refreshToken`，持久化到 `auth_refresh_token`
  - refresh token 绑定 `deviceId`，刷新时必须匹配设备
  - `POST /api/auth/refresh` 使用 refresh token 换发新 access token + refresh token（轮换旧 token）
  - 若检测到已撤销 refresh token 被再次使用（重放），系统会撤销该用户全部活跃 refresh token
  - `POST /api/auth/logout` 会将当前 access token 的 `jti` 写入黑名单，并可选撤销 refresh token
- 会话管理：
  - `GET /api/auth/sessions` 查询当前账号活跃设备会话
  - `POST /api/auth/sessions/revoke-device` 按设备下线
  - `POST /api/auth/sessions/revoke-all` 当前账号全设备下线
- 管理员会话审计：
  - `GET /api/admin/auth/sessions?username={username}` 查看指定用户会话
  - `GET /api/admin/auth/security-events/summary?lookbackMinutes=60` 汇总认证安全事件
  - `GET /api/admin/auth/security-events/timeline?lookbackMinutes=60&actionTypes=...` 返回按分钟聚合时间序列
  - `GET /api/admin/auth/security-events/risk-users-top?lookbackMinutes=60&topN=10&actionTypes=...` 返回异常用户名 TopN
  - `GET /api/admin/auth/security-events/export?type=summary|timeline|risk-users-top&format=json|csv` 导出安全数据
  - `POST /api/admin/auth/security-events/export/tasks` 创建导出任务
  - `GET /api/admin/auth/security-events/export/tasks/{taskId}` 查询导出任务状态
  - `GET /api/admin/auth/security-events/export/tasks/{taskId}/download` 下载导出结果
  - `POST /api/admin/auth/security-events/export/tasks/{taskId}/retry` 重试失败任务
  - `GET /api/admin/auth/security-events/export/tasks?page=0&size=20&status=SUCCESS` 分页查询任务
  - `POST /api/admin/auth/security-events/export/tasks/cleanup` 手工清理过期任务
  - 异步导出支持 `idempotencyKey` 幂等复用（同键复用 RUNNING/SUCCESS 任务）
  - `POST /api/admin/auth/sessions/revoke-device` 管理员按设备强制下线
  - `POST /api/admin/auth/sessions/revoke-all` 管理员强制用户全设备下线
- 安全审计日志：
  - 所有关键会话动作会写入 `operation_audit_log`（`targetType=AUTH_SESSION`）
  - 可通过现有审计查询接口按 `actionType`/`targetId` 检索

### 3) RBAC 规则（URL 级）

- 放行：
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `POST /api/payment/callback`
  - `/products/**`
- 角色控制：
  - `/api/admin/**` -> `ROLE_ADMIN`
  - `/api/recycle/**`、`/api/mall/**`、`/api/auth/me`、`/api/auth/logout` -> 已登录可访问

### 4) 错误返回

为保证接口风格统一，401/403 由 Security 层返回统一 JSON：

- 401：`{"success":false,"message":"请先登录","data":null}`
- 403：`{"success":false,"message":"无权限访问该资源","data":null}`

### 5) 配置项

`application.yml` / `application-mysql.yml`：

```yaml
security:
  auth:
    jwt:
      secret: recycle-mall-demo-jwt-secret-key-please-change
      expire-minutes: 120
      refresh-expire-minutes: 10080
    export-task:
      retain-days: 7
      cleanup-fixed-delay-ms: 3600000
      max-running: 3
      default-max-retry: 2
      running-timeout-minutes: 10
```

- `secret`：JWT HS256 对称密钥（生产环境必须替换并使用安全存储）
- `expire-minutes`：Access Token 有效期（分钟）
- `refresh-expire-minutes`：Refresh Token 有效期（分钟）
- `export-task.retain-days`：导出任务保留天数（定时清理）
- `export-task.cleanup-fixed-delay-ms`：导出任务自动清理调度间隔（毫秒）
- `export-task.max-running`：导出任务最大并发运行数
- `export-task.default-max-retry`：导出任务默认最大重试次数
- `export-task.running-timeout-minutes`：RUNNING 任务超时分钟数（超时自动标记失败）

默认演示账号（`data.sql`）：

- 管理员：`admin / admin123`
- 普通用户：`alice / user123`、`bob / user123`

> 说明：演示数据使用 `{noop}` 明文密码仅用于本地开发。生产环境请改为 `{bcrypt}` 并替换为加密密码串。

认证接口示例：

```bash
# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123","deviceId":"web-chrome-01"}'

# 刷新 Access Token（携带登录返回的 refreshToken）
curl -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refresh_token>","deviceId":"web-chrome-01"}'

# 查询当前登录用户
curl http://localhost:8080/api/auth/me \
  -H 'Authorization: Bearer <token>'

# 登出（建议带 refreshToken 一并撤销）
curl -X POST http://localhost:8080/api/auth/logout \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refresh_token>"}'

# 查看活跃会话
curl http://localhost:8080/api/auth/sessions \
  -H 'Authorization: Bearer <token>'

# 按设备下线
curl -X POST http://localhost:8080/api/auth/sessions/revoke-device \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"deviceId":"web-chrome-01"}'

# 全设备下线
curl -X POST http://localhost:8080/api/auth/sessions/revoke-all \
  -H 'Authorization: Bearer <token>'

# 管理员查询指定用户会话
curl "http://localhost:8080/api/admin/auth/sessions?username=alice" \
  -H 'Authorization: Bearer <admin_token>'

# 管理员查看安全事件汇总（近 120 分钟）
curl "http://localhost:8080/api/admin/auth/security-events/summary?lookbackMinutes=120" \
  -H 'Authorization: Bearer <admin_token>'

# 管理员查看安全事件时间序列（近 60 分钟，分钟级）
curl "http://localhost:8080/api/admin/auth/security-events/timeline?lookbackMinutes=60&actionTypes=AUTH_REFRESH_REPLAY_BLOCKED&actionTypes=AUTH_LOGOUT" \
  -H 'Authorization: Bearer <admin_token>'

# 管理员查看风险用户 TopN（按安全事件次数）
curl "http://localhost:8080/api/admin/auth/security-events/risk-users-top?lookbackMinutes=120&topN=10&actionTypes=AUTH_REFRESH_REPLAY_BLOCKED&actionTypes=AUTH_ADMIN_SESSION_REVOKE_DEVICE" \
  -H 'Authorization: Bearer <admin_token>'

# 导出 summary 为 CSV
curl "http://localhost:8080/api/admin/auth/security-events/export?type=summary&format=csv&lookbackMinutes=60" \
  -H 'Authorization: Bearer <admin_token>'

# 创建异步导出任务
curl -X POST "http://localhost:8080/api/admin/auth/security-events/export/tasks" \
  -H 'Authorization: Bearer <admin_token>' \
  -H 'Content-Type: application/json' \
  -d '{"type":"timeline","format":"json","lookbackMinutes":120,"topN":10,"actionTypes":["AUTH_REFRESH_REPLAY_BLOCKED","AUTH_LOGOUT"],"idempotencyKey":"export-20260428-001"}'

# 查询导出任务状态
curl "http://localhost:8080/api/admin/auth/security-events/export/tasks/<task_id>" \
  -H 'Authorization: Bearer <admin_token>'

# 重试失败任务
curl -X POST "http://localhost:8080/api/admin/auth/security-events/export/tasks/<task_id>/retry" \
  -H 'Authorization: Bearer <admin_token>' \
  -H 'Content-Type: application/json' \
  -d '{"lookbackMinutes":60,"topN":10}'

# 下载导出结果
curl "http://localhost:8080/api/admin/auth/security-events/export/tasks/<task_id>/download" \
  -H 'Authorization: Bearer <admin_token>'

# 分页查询导出任务
curl "http://localhost:8080/api/admin/auth/security-events/export/tasks?page=0&size=20&status=SUCCESS" \
  -H 'Authorization: Bearer <admin_token>'

# 手工清理导出任务（保留近 7 天）
curl -X POST "http://localhost:8080/api/admin/auth/security-events/export/tasks/cleanup" \
  -H 'Authorization: Bearer <admin_token>' \
  -H 'Content-Type: application/json' \
  -d '{"retainDays":7}'

# 管理员按设备下线
curl -X POST http://localhost:8080/api/admin/auth/sessions/revoke-device \
  -H 'Authorization: Bearer <admin_token>' \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","deviceId":"iphone-1"}'

# 管理员全设备下线
curl -X POST http://localhost:8080/api/admin/auth/sessions/revoke-all \
  -H 'Authorization: Bearer <admin_token>' \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice"}'
```

登录响应示例：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "refreshToken": "4f...b1",
    "refreshExpiresIn": 604800,
    "deviceId": "web-chrome-01",
    "username": "admin",
    "role": "ADMIN"
  }
}
```

### 6) 安全链路测试

新增集成测试：`src/test/java/com/recycle/mall/interfaces/http/AuthSecurityIntegrationTest.java`

覆盖场景：

- 登录成功后访问 `/api/auth/me`
- 未携带 Token 访问 `/api/auth/me` 返回 401
- `USER` 角色访问 `/api/admin/**` 返回 403
- `ADMIN` 角色访问 `/api/admin/**` 成功
- 使用 refresh token 刷新访问令牌
- 登出后 access token 命中黑名单并返回 401
- refresh token 重放防护（重复使用旧 refresh token 会触发阻断）
- refresh token 设备绑定校验（deviceId 不匹配拒绝刷新）
- 会话管理（会话列表、按设备下线、全设备下线）
- 管理员会话审计与强制下线（`/api/admin/auth/sessions*`）

### 7) 会话安全审计动作（operation_audit_log）

本次新增以下 `actionType`：

- `AUTH_LOGIN_SUCCESS`：登录成功
- `AUTH_REFRESH_SUCCESS`：refresh 成功换发
- `AUTH_REFRESH_REPLAY_BLOCKED`：检测到 refresh token 重放并阻断
- `AUTH_LOGOUT`：用户登出（含 access token 黑名单）
- `AUTH_SESSION_REVOKE_DEVICE`：用户自助按设备下线
- `AUTH_SESSION_REVOKE_ALL`：用户自助全设备下线
- `AUTH_ADMIN_SESSION_QUERY`：管理员查询指定用户会话
- `AUTH_ADMIN_SESSION_REVOKE_DEVICE`：管理员按设备强制下线
- `AUTH_ADMIN_SESSION_REVOKE_ALL`：管理员强制全设备下线
- `AUTH_EXPORT_TASK_CREATED`：创建导出任务
- `AUTH_EXPORT_TASK_SUCCESS`：导出任务执行成功
- `AUTH_EXPORT_TASK_FAILED`：导出任务执行失败
- `AUTH_EXPORT_TASK_RETRY`：导出任务重试
- `AUTH_EXPORT_TASK_TIMEOUT`：导出任务运行超时并自动失败

### 8) 告警规则建议（可直接接监控平台）

推荐先接入以下阈值告警：

- **高危重放告警**：`AUTH_REFRESH_REPLAY_BLOCKED` 在 5 分钟内 >= 3 次
- **管理员强制下线激增**：`AUTH_ADMIN_SESSION_REVOKE_ALL` 在 10 分钟内 >= 5 次
- **异常设备下线激增**：`AUTH_SESSION_REVOKE_DEVICE` + `AUTH_ADMIN_SESSION_REVOKE_DEVICE` 在 10 分钟内 >= 10 次
- **导出任务异常激增**：`AUTH_EXPORT_TASK_FAILED` + `AUTH_EXPORT_TASK_TIMEOUT` 在 10 分钟内 >= 3 次

可通过以下管理接口获取聚合数据后做告警判断：

- `GET /api/admin/auth/security-events/summary?lookbackMinutes=5`
- `GET /api/admin/auth/security-events/summary?lookbackMinutes=10`
- `GET /api/admin/auth/security-events/timeline?lookbackMinutes=60`（适合画趋势图）
- `GET /api/admin/auth/security-events/risk-users-top?lookbackMinutes=60&topN=10`（定位异常账号）

### 9) 导出任务状态机与错误码

导出任务状态流转：

`RUNNING -> SUCCESS`  
`RUNNING -> FAILED`（执行异常）  
`RUNNING -> FAILED`（超时自动终止）  
`FAILED -> RUNNING -> SUCCESS/FAILED`（重试）

导出任务错误码（`errorCode`）：

- `NONE`：无错误（运行中或成功）
- `EXPORT_EXECUTION_FAILED`：导出执行异常失败
- `EXPORT_TASK_TIMEOUT`：任务运行超时失败

### 10) 全局异常处理测试

新增集成测试：`src/test/java/com/recycle/mall/interfaces/http/GlobalExceptionHandlerIntegrationTest.java`

覆盖场景：

- `IllegalArgumentException` -> `400`
- `MethodArgumentNotValidException` -> `400`
- `ConstraintViolationException` -> `400`
- `MissingServletRequestParameterException` -> `400`
- `MethodArgumentTypeMismatchException` -> `400`
- `HttpMessageNotReadableException` -> `400`
- `ObjectOptimisticLockingFailureException` -> `409`
- `DataIntegrityViolationException` -> `409`
- 兜底 `Exception` -> `500`

测试目标：确保全局异常返回遵循统一 `ApiResponse` 结构，并保持 HTTP 状态语义一致。

### 11) 管理端缓存协商测试

新增集成测试：`src/test/java/com/recycle/mall/interfaces/http/AdminRecycleCacheIntegrationTest.java`

Checklist（管理端缓存协商公共字段）：

- [ ] `GET /api/admin/recycle/review-strategy` 返回 `ETag`/`Last-Modified`（`shouldReturn304ForReviewStrategyWhenIfNoneMatchMatches`）
- [ ] `GET /api/admin/recycle/error-codes/global` 返回 `ETag`/`Last-Modified`（`shouldReturn304ForErrorCodesWhenIfNoneMatchMatches`）
- [ ] 携带 `If-None-Match` 命中时返回 `304 Not Modified`（`shouldReturn304ForReviewStrategyWhenIfNoneMatchMatches`、`shouldReturn304ForErrorCodesWhenIfNoneMatchMatches`、`shouldReturn304ForConfigCenterBundleWhenIfNoneMatchMatches`）
- [ ] 策略更新后（`POST /api/admin/recycle/review-strategy/update`）`ETag` 发生变化（`shouldChangeReviewStrategyEtagAfterUpdate`）
- [ ] 非法模块名访问 `GET /api/admin/recycle/config-center/module/{moduleName}` 返回 `400`（`shouldReturn400WhenModuleNameUnsupported`）

### 12) C 端缓存协商测试

新增集成测试：`src/test/java/com/recycle/mall/interfaces/http/ResaleOrderListIntegrationTest.java`

Checklist（订单列表/状态字典/订单概览缓存协商公共字段）：

- [ ] `GET /api/mall/orders` 返回 `ETag` 与 `X-Cache-Digest`（`shouldListBuyerOrdersByCreatedAtDescAndSupportLimit`）
- [ ] `GET /api/mall/orders` 返回体 `data.cacheDigest` 存在（`shouldListBuyerOrdersByCreatedAtDescAndSupportLimit`）
- [ ] `GET /api/mall/orders` 携带 `If-None-Match` 命中时返回 `304 Not Modified`（`shouldReturn304ForBuyerOrderListWhenIfNoneMatchMatches`）
- [ ] `GET /api/mall/orders/status-dictionary` 返回 `ETag`/`Last-Modified`/`X-Cache-Digest`（`shouldReturn304ForOrderStatusDictionaryWhenIfNoneMatchMatches`）
- [ ] `GET /api/mall/orders/status-dictionary` 返回体 `data.cacheDigest` 存在（`shouldReturnOrderStatusDictionary`）
- [ ] `GET /api/mall/orders/status-dictionary` 携带 `If-None-Match` 命中时返回 `304 Not Modified`（`shouldReturn304ForOrderStatusDictionaryWhenIfNoneMatchMatches`）
- [ ] `GET /api/mall/orders/summary` 返回 `ETag` 与 `X-Cache-Digest`（`shouldReturnBuyerOrderSummary`）
- [ ] `GET /api/mall/orders/summary` 返回体 `data.cacheDigest` 存在（`shouldReturnBuyerOrderSummary`）
- [ ] `GET /api/mall/orders/summary` 携带 `If-None-Match` 命中时返回 `304 Not Modified`（`shouldReturn304ForBuyerOrderSummaryWhenIfNoneMatchMatches`）

缓存协商回归执行命令示例：

```bash
# 按测试类执行（管理端 + C 端）
mvn -Dtest=AdminRecycleCacheIntegrationTest,ResaleOrderListIntegrationTest test

# 仅执行 C 端缓存协商核心用例
mvn -Dtest=ResaleOrderListIntegrationTest#shouldReturn304ForBuyerOrderListWhenIfNoneMatchMatches test
mvn -Dtest=ResaleOrderListIntegrationTest#shouldReturn304ForOrderStatusDictionaryWhenIfNoneMatchMatches test
mvn -Dtest=ResaleOrderListIntegrationTest#shouldReturn304ForBuyerOrderSummaryWhenIfNoneMatchMatches test

# 仅执行管理端缓存协商核心用例
mvn -Dtest=AdminRecycleCacheIntegrationTest#shouldReturn304ForReviewStrategyWhenIfNoneMatchMatches test
mvn -Dtest=AdminRecycleCacheIntegrationTest#shouldReturn304ForErrorCodesWhenIfNoneMatchMatches test
```

若本机未安装 Maven，可直接使用项目脚本：

```bash
# 项目内统一测试入口
./scripts/run-tests.sh all

# 仅做脚本自检（验证执行环境）
./scripts/run-tests.sh self-check --json
```
