package com.recycle.mall.common;

/**
 * 统一业务错误码
 * <p>
 * 命名规则：{模块}_{场景}_{原因}
 * <ul>
 *   <li>AUTH_* — 认证鉴权相关</li>
 *   <li>ORDER_* — 订单相关</li>
 *   <li>PAYMENT_* — 支付相关</li>
 *   <li>REVIEW_* — 评价相关</li>
 *   <li>PARAM_* — 参数校验相关</li>
 *   <li>SYS_* — 系统级错误</li>
 * </ul>
 */
public final class ErrorCode {

    private ErrorCode() {}

    // ==================== 参数校验 ====================

    /** 参数校验失败（通用） */
    public static final String PARAM_INVALID = "PARAM_INVALID";
    /** 缺少必填参数 */
    public static final String PARAM_MISSING = "PARAM_MISSING";
    /** 参数类型错误 */
    public static final String PARAM_TYPE_MISMATCH = "PARAM_TYPE_MISMATCH";
    /** 请求体格式错误 */
    public static final String PARAM_BODY_MALFORMED = "PARAM_BODY_MALFORMED";

    // ==================== 认证鉴权 ====================

    /** 未登录 / Token 无效或过期 */
    public static final String AUTH_UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    /** 无权限访问 */
    public static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    /** 用户名或密码错误 */
    public static final String AUTH_BAD_CREDENTIALS = "AUTH_BAD_CREDENTIALS";
    /** 账号不可用 */
    public static final String AUTH_ACCOUNT_DISABLED = "AUTH_ACCOUNT_DISABLED";
    /** Refresh Token 无效 */
    public static final String AUTH_REFRESH_TOKEN_INVALID = "AUTH_REFRESH_TOKEN_INVALID";
    /** Refresh Token 已过期 */
    public static final String AUTH_REFRESH_TOKEN_EXPIRED = "AUTH_REFRESH_TOKEN_EXPIRED";
    /** Refresh Token 设备不匹配 */
    public static final String AUTH_REFRESH_TOKEN_DEVICE_MISMATCH = "AUTH_REFRESH_TOKEN_DEVICE_MISMATCH";
    /** Refresh Token 重放被阻断 */
    public static final String AUTH_REFRESH_REPLAY_BLOCKED = "AUTH_REFRESH_REPLAY_BLOCKED";
    /** Token 已被撤销（黑名单） */
    public static final String AUTH_TOKEN_REVOKED = "AUTH_TOKEN_REVOKED";

    // ==================== 订单相关 ====================

    /** 订单不存在 */
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    /** 订单状态不允许该操作 */
    public static final String ORDER_STATUS_CONFLICT = "ORDER_STATUS_CONFLICT";
    /** 下单冲突（乐观锁） */
    public static final String ORDER_CONCURRENT_CONFLICT = "ORDER_CONCURRENT_CONFLICT";
    /** 商品已下架或售罄 */
    public static final String ORDER_LISTING_UNAVAILABLE = "ORDER_LISTING_UNAVAILABLE";
    /** 库存不足 */
    public static final String ORDER_STOCK_INSUFFICIENT = "ORDER_STOCK_INSUFFICIENT";
    /** 非本人订单 */
    public static final String ORDER_NOT_OWNER = "ORDER_NOT_OWNER";
    /** 幂等键已用于其他订单 */
    public static final String ORDER_IDEMPOTENT_KEY_CONFLICT = "ORDER_IDEMPOTENT_KEY_CONFLICT";

    // ==================== 支付相关 ====================

    /** 支付签名校验失败 */
    public static final String PAYMENT_SIGNATURE_INVALID = "PAYMENT_SIGNATURE_INVALID";
    /** 支付 nonce 重放 */
    public static final String PAYMENT_NONCE_REPLAY = "PAYMENT_NONCE_REPLAY";
    /** 支付请求已过期 */
    public static final String PAYMENT_REQUEST_EXPIRED = "PAYMENT_REQUEST_EXPIRED";
    /** 数据冲突 */
    public static final String PAYMENT_DATA_CONFLICT = "PAYMENT_DATA_CONFLICT";

    // ==================== 评价相关 ====================

    /** 评价不存在 */
    public static final String REVIEW_NOT_FOUND = "REVIEW_NOT_FOUND";
    /** 订单未完成不可评价 */
    public static final String REVIEW_ORDER_NOT_COMPLETED = "REVIEW_ORDER_NOT_COMPLETED";
    /** 订单已评价 */
    public static final String REVIEW_ALREADY_EXISTS = "REVIEW_ALREADY_EXISTS";
    /** 超过追评窗口 */
    public static final String REVIEW_APPEND_WINDOW_EXPIRED = "REVIEW_APPEND_WINDOW_EXPIRED";
    /** 举报工单不存在 */
    public static final String REVIEW_REPORT_NOT_FOUND = "REVIEW_REPORT_NOT_FOUND";
    /** 举报工单状态不允许处理 */
    public static final String REVIEW_REPORT_STATUS_CONFLICT = "REVIEW_REPORT_STATUS_CONFLICT";

    // ==================== 回收相关 ====================

    /** 回收单不存在 */
    public static final String RECYCLE_ORDER_NOT_FOUND = "RECYCLE_ORDER_NOT_FOUND";
    /** 图片审核不通过 */
    public static final String RECYCLE_IMAGE_AUDIT_FAILED = "RECYCLE_IMAGE_AUDIT_FAILED";
    /** 物流单不存在 */
    public static final String RECYCLE_LOGISTICS_NOT_FOUND = "RECYCLE_LOGISTICS_NOT_FOUND";

    // ==================== 系统级 ====================

    /** 系统内部错误 */
    public static final String SYS_INTERNAL_ERROR = "SYS_INTERNAL_ERROR";
    /** 操作过于频繁（乐观锁） */
    public static final String SYS_OPTIMISTIC_LOCK = "SYS_OPTIMISTIC_LOCK";
    /** 配置更新内容为空 */
    public static final String SYS_CONFIG_UPDATE_EMPTY = "SYS_CONFIG_UPDATE_EMPTY";
}
