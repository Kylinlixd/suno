# Recycle Mall 业务流程图

## 全链路业务总览

```mermaid
flowchart TB
    subgraph C端用户
        U1[👤 用户提交回收]
        U2[🛒 浏览二销商城]
        U3[📦 下单购买]
        U4[💳 支付订单]
        U5[✅ 确认收货]
        U6[⭐ 提交评价]
    end

    subgraph B端平台
        P1[🔍 图片审核]
        P2[📋 SN解析]
        P3[💰 自动估价]
        P4[📝 生成回收单]
        P5[🚚 物流追踪]
        P6[✅ 质检审核]
        P7[💲 定价审核]
        P8[🏪 上架二销]
        P9[🚛 发货]
        P10[🔄 退款处理]
    end

    subgraph 支付系统
        PAY1[🔐 签名校验]
        PAY2[🛡️ 幂等防重]
        PAY3[📝 回调日志]
        PAY4[🔁 重放队列]
    end

    subgraph 安全与审计
        S1[🔑 JWT认证]
        S2[🛡️ RBAC鉴权]
        S3[📜 审计日志]
        S4[📊 安全事件]
    end

    U1 --> P1 --> P2 --> P3 --> P4
    P4 --> P5
    P5 --> P6 --> P7 --> P8
    P8 --> U2 --> U3 --> U4
    U4 --> PAY1 --> PAY2 --> PAY3
    PAY3 --> PAY4
    U4 --> P9 --> U5 --> U6

    P6 & P7 & P8 & P9 & P10 -.-> S3
    U1 & U4 -.-> S1
    S1 -.-> S2 -.-> S4

    style U1 fill:#4FC3F7,color:#000
    style U2 fill:#4FC3F7,color:#000
    style U3 fill:#4FC3F7,color:#000
    style U4 fill:#4FC3F7,color:#000
    style U5 fill:#4FC3F7,color:#000
    style U6 fill:#4FC3F7,color:#000
    style P1 fill:#FFB74D,color:#000
    style P2 fill:#FFB74D,color:#000
    style P3 fill:#FFB74D,color:#000
    style P4 fill:#FFB74D,color:#000
    style P5 fill:#FFB74D,color:#000
    style P6 fill:#FFB74D,color:#000
    style P7 fill:#FFB74D,color:#000
    style P8 fill:#FFB74D,color:#000
    style P9 fill:#FFB74D,color:#000
    style P10 fill:#FFB74D,color:#000
    style PAY1 fill:#CE93D8,color:#000
    style PAY2 fill:#CE93D8,color:#000
    style PAY3 fill:#CE93D8,color:#000
    style PAY4 fill:#CE93D8,color:#000
    style S1 fill:#A5D6A7,color:#000
    style S2 fill:#A5D6A7,color:#000
    style S3 fill:#A5D6A7,color:#000
    style S4 fill:#A5D6A7,color:#000
```

---

## 1. C2B 回收流程

```mermaid
flowchart LR
    A[👤 用户提交回收申请] --> B{🔍 AI图片审核}
    B -->|通过| C[📋 SN码解析]
    B -->|不通过| X1[❌ 拒绝：请重新上传]
    C --> D[💰 自动估价]
    D --> E[📝 生成回收单]
    E --> F[🚚 生成物流单]

    subgraph 回收单状态流转
        F --> G[CREATED]
        G -->|QUALITY_CHECK| H[QUALITY_CHECKED]
        H -->|PRICE_REVIEW| I[PRICE_REVIEWED]
        I -->|LIST_ON_SHELF| J[LISTED]
    end

    E --> G
    G --> K[🎁 积分发放]

    style A fill:#4FC3F7,color:#000
    style X1 fill:#EF5350,color:#fff
    style G fill:#FFF176,color:#000
    style H fill:#FFF176,color:#000
    style I fill:#FFF176,color:#000
    style J fill:#81C784,color:#000
    style K fill:#A5D6A7,color:#000
```

### 回收单状态机

| 当前状态 | 动作 | 下一状态 | 说明 |
|----------|------|----------|------|
| `CREATED` | `QUALITY_CHECK` | `QUALITY_CHECKED` | 质检通过 |
| `QUALITY_CHECKED` | `PRICE_REVIEW` | `PRICE_REVIEWED` | 定价审核 |
| `PRICE_REVIEWED` | `LIST_ON_SHELF` | `LISTED` | 上架二销 |

### 估价算法

```
输入：品牌 + 型号 + 生产日期 + 磨损分
  ↓
规则表匹配（优先级：精确 > 通配 > 兜底）
  ↓
输出：品阶（GOOD/MEDIUM/UNQUALIFIED）+ 估价
```

---

### 2. B2C 二销商城流程

```mermaid
flowchart LR
    A[🏪 发布二销商品] --> B[🛒 C端浏览商品]
    B --> C[📦 创建订单]
    C --> D[💳 支付]

    subgraph 订单支付状态
        D --> E[UNPAID → PAID]
    end

    subgraph 订单履约状态
        C --> F[WAIT_PAY]
        E --> G[TO_DELIVER]
        G -->|管理员发货| H[DELIVERED]
        H -->|用户确认收货| I[COMPLETED]
        F -->|超时/取消| J[CANCELLED]
        G -->|退款| K[REFUNDED]
    end

    I --> L[⭐ 提交评价]
    L --> M[💬 追评]
    L --> N[👍 有用性投票]
    L --> O[🚩 举报评价]

    style A fill:#FFB74D,color:#000
    style C fill:#4FC3F7,color:#000
    style E fill:#81C784,color:#000
    style I fill:#81C784,color:#000
    style J fill:#EF5350,color:#fff
    style K fill:#EF5350,color:#fff
```

### 二销订单状态机

**支付状态流转：**

| 当前状态 | 触发事件 | 下一状态 |
|----------|----------|----------|
| `UNPAID` | 支付成功 | `PAID` |

**履约状态流转：**

| 当前状态 | 触发事件 | 下一状态 |
|----------|----------|----------|
| `WAIT_PAY` | 支付成功 | `TO_DELIVER` |
| `WAIT_PAY` | 取消订单 | `CANCELLED`（恢复库存） |
| `TO_DELIVER` | 管理员发货 | `DELIVERED` |
| `DELIVERED` | 用户确认收货 | `COMPLETED` |
| `TO_DELIVER` | 退款 | `REFUNDED` |

---

## 3. 支付回调与重放流程

```mermaid
flowchart TB
    A[🏦 支付网关回调] --> B[🔐 签名校验]
    B -->|校验失败| X1[❌ 拒绝]
    B -->|校验通过| C{payStatus?}
    C -->|非SUCCESS| D[⚠️ 忽略：记录日志]
    C -->|SUCCESS| E[🛡️ 幂等检查]

    E -->|首次| F[✅ 标记订单已支付]
    E -->|重复| G[🔄 返回幂等快照]

    F --> H[📝 回调日志落库]
    D --> H
    X1 --> H

    H --> I{回调是否成功?}
    I -->|成功| END1[✅ 结束]
    I -->|失败| J[🔁 入队重放]

    subgraph 重放队列
        J --> K[PENDING]
        K -->|消费| L[PROCESSING]
        L -->|成功| M[SUCCESS]
        L -->|失败+重试| K
        L -->|超过最大重试| N[DEAD]
        N -->|人工再投递| K
    end

    subgraph 半自动处置
        O[auto-handle] --> P[消费 PENDING]
        P --> Q[可选：自动重投 DEAD]
        O -.->|traceId幂等| R[幂等记录表]
    end

    style A fill:#CE93D8,color:#000
    style F fill:#81C784,color:#000
    style G fill:#FFF176,color:#000
    style X1 fill:#EF5350,color:#fff
    style N fill:#EF5350,color:#fff
    style M fill:#81C784,color:#000
```

### 重放退避策略

```
backoff = min(base × 2^(retry-1), max)
默认：base=5s, max=300s, maxRetry=3
```

---

## 4. 认证鉴权流程

```mermaid
flowchart TB
    A[👤 用户登录] --> B[🔐 AuthenticationManager 认证]
    B -->|认证失败| X1[❌ 拒绝登录]
    B -->|认证成功| C[🔑 签发 JWT Access Token]
    C --> D[🔄 签发 Refresh Token]
    D --> E[💾 持久化 Refresh Token<br/>绑定设备 deviceId]

    subgraph 请求鉴权
        F[📥 携带 Bearer Token 请求] --> G[JwtDecoder 校验]
        G -->|签名/有效期校验失败| X2[❌ 401]
        G -->|jti 在黑名单中| X3[❌ 令牌已撤销]
        G -->|校验通过| H[✅ 提取角色权限]
        H --> I{RBAC 鉴权}
        I -->|无权限| X4[❌ 403]
        I -->|有权限| J[✅ 放行]
    end

    subgraph Token 刷新
        K[🔄 Refresh Token 换发] --> L{Token 有效?}
        L -->|已撤销| M[🚨 检测重放！<br/>撤销该用户全部 Refresh Token]
        L -->|已过期| X5[❌ Token 已失效]
        L -->|有效且设备匹配| N[✅ 签发新 Token 对<br/>旧 Refresh Token 失效]
    end

    subgraph 登出
        O[🚪 登出] --> P[📋 JWT jti 加入黑名单]
        O --> Q[🔄 可选：撤销 Refresh Token]
    end

    style A fill:#4FC3F7,color:#000
    style C fill:#A5D6A7,color:#000
    style D fill:#A5D6A7,color:#000
    style X1 fill:#EF5350,color:#fff
    style X2 fill:#EF5350,color:#fff
    style X3 fill:#EF5350,color:#fff
    style X4 fill:#EF5350,color:#fff
    style M fill:#FF7043,color:#fff
```

### RBAC 权限矩阵

| 路径模式 | 所需权限 |
|----------|----------|
| `POST /api/auth/login` | 公开 |
| `POST /api/auth/refresh` | 公开 |
| `POST /api/payment/callback` | 公开 |
| `/products/**` | 公开 |
| `/api/admin/**` | `ROLE_ADMIN` |
| `/api/recycle/**` | 已登录 |
| `/api/mall/**` | 已登录 |
| `/api/auth/me`, `/logout`, `/sessions/**` | 已登录 |

---

## 5. 评价系统流程

```mermaid
flowchart LR
    A[⭐ 首次评价] --> B[📝 评价入库]
    B --> C{敏感词检测}
    C -->|命中| D[⚠️ 标记敏感<br/>moderation_status=SENSITIVE]
    C -->|正常| E[✅ moderation_status=NORMAL]

    B --> F[💬 追评<br/>30天内]
    B --> G[🏪 商家回复]

    subgraph 评价交互
        H[👍 有用性投票]
        I[🚩 举报评价]
    end

    I --> J[📋 生成举报工单]
    J --> K[👮 管理员处理]
    K -->|HIDE_REVIEW| L[隐藏评价]
    K -->|DISMISS_REPORT| M[驳回举报]
    K -->|BAN_USER| N[封禁用户]

    subgraph 智能排序
        O[📊 评价列表排序] --> P[SMART 综合排序<br/>权重=时间衰减 × 有用性 × 品质]
    end

    style A fill:#4FC3F7,color:#000
    style D fill:#FF7043,color:#fff
    style E fill:#81C784,color:#000
    style L fill:#EF5350,color:#fff
```

---

## 6. 配置中心流程

```mermaid
flowchart LR
    A[📱 前端启动] --> B[GET /bundle<br/>拉取聚合配置包]
    B --> C[缓存本地 digest]

    A --> D[GET /modules<br/>获取模块摘要]
    D --> E[POST /module-diff<br/>比对本地与服务端差异]
    E -->|有差异| F[GET /module/{name}<br/>按需拉取变化模块]
    E -->|无差异| G[✅ 使用本地缓存]

    subgraph 热更新
        H[POST /review-strategy/update<br/>热更新评价策略]
        I[POST /alert-noise-rules/update<br/>热更新降噪规则]
    end

    subgraph ETag缓存协商
        J[If-None-Match] -->|匹配| K[304 Not Modified]
        J -->|不匹配| L[200 + 新 ETag]
    end

    style A fill:#4FC3F7,color:#000
    style F fill:#81C784,color:#000
    style G fill:#A5D6A7,color:#000
    style K fill:#FFF176,color:#000
```

---

## 7. 定时任务

```mermaid
flowchart TB
    subgraph 支付相关
        T1[⏰ Nonce 清理<br/>过期 nonce 删除]
        T2[⏰ 幂等记录清理<br/>过期记录删除]
        T3[⏰ 重放任务消费<br/>PENDING → PROCESSING]
    end

    subgraph 订单相关
        T4[⏰ 超时未支付关单<br/>WAIT_PAY 超时 → CANCELLED]
        T5[⏰ 自动确认收货<br/>DELIVERED 超时 → COMPLETED]
    end

    T3 --> A{消费结果}
    A -->|成功| B[SUCCESS]
    A -->|失败| C[退避等待 → PENDING]
    A -->|超过最大重试| D[DEAD]

    style T1 fill:#CE93D8,color:#000
    style T2 fill:#CE93D8,color:#000
    style T3 fill:#CE93D8,color:#000
    style T4 fill:#FFB74D,color:#000
    style T5 fill:#FFB74D,color:#000
    style D fill:#EF5350,color:#fff
```

---

## 数据实体关系

```mermaid
erDiagram
    user_account ||--o{ recycle_order : "创建"
    user_account ||--o{ resale_order : "购买"
    user_account ||--o{ points_ledger : "积分流水"
    user_account ||--o{ resale_favorite : "收藏"
    user_account ||--o{ resale_review : "评价"
    user_account ||--o{ resale_review_vote : "投票"
    user_account ||--o{ resale_review_report : "举报"
    user_account ||--o{ auth_refresh_token : "会话"

    product ||--o{ recycle_order : "回收"
    product ||--o{ resale_listing : "二销"

    recycle_order ||--o{ logistics_track : "物流"
    recycle_order ||--o{ resale_listing : "上架"

    resale_listing ||--o{ resale_order : "下单"
    resale_listing ||--o{ resale_favorite : "收藏"
    resale_listing ||--o{ resale_review : "评价"

    resale_order ||--o| resale_review : "评价"
    resale_order ||--o| payment_idempotency : "幂等"

    resale_review ||--o{ resale_review_vote : "投票"
    resale_review ||--o{ resale_review_report : "举报"

    payment_callback_log ||--o{ payment_replay_task : "重放"

    user_account {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR password_hash
        VARCHAR role_code
        VARCHAR account_status
        VARCHAR level
        INT points
    }

    recycle_order {
        BIGINT id PK
        VARCHAR order_no UK
        BIGINT user_id FK
        BIGINT product_id FK
        DECIMAL estimated_price
        VARCHAR grade
        VARCHAR status
    }

    resale_listing {
        BIGINT id PK
        BIGINT recycle_order_id FK
        BIGINT product_id FK
        DECIMAL sale_price
        INT stock
        VARCHAR status
        BIGINT version
    }

    resale_order {
        BIGINT id PK
        VARCHAR order_no UK
        BIGINT buyer_user_id FK
        BIGINT listing_id FK
        DECIMAL amount
        VARCHAR pay_status
        VARCHAR fulfill_status
    }
