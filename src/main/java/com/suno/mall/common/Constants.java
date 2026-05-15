
package com.suno.mall.common;

/**
 * 系统公共常量
 */
public final class Constants {

    private Constants() {}

    // ==================== 语言常量 ====================
    public static final String DEFAULT_LANG = "zh-CN";
    public static final String LANG_ZH_CN = "zh-CN";
    public static final String LANG_EN_US = "en-US";

    // ==================== 版本常量 ====================
    public static final String DIAGNOSIS_SCHEMA_VERSION = "1.0.0";
    public static final String CLEANUP_PERFORMANCE_CHECK_SCHEMA_VERSION = "1.0.0";
    public static final String HEALTH_SCHEMA_VERSION = "1.0.0";
    public static final String QUERY_AUDIT_ACTIONS_SCHEMA_VERSION = "1.0.0";
    public static final String STATUS_DICTIONARY_VERSION = "1.0.0";
    public static final String GLOBAL_ERROR_CODE_DICT_VERSION = "1.0.0";
    public static final String ALERT_NOISE_RULES_VERSION = "1.0.0";
    public static final String CONFIG_CENTER_BUNDLE_VERSION = "1.0.0";
    public static final String DEGRADE_ACTION_DICT_VERSION = "1.0.0";

    // ==================== 转售上架状态常量 ====================
    public static final String LISTING_STATUS_ON_SHELF = "ON_SHELF";
    public static final String LISTING_STATUS_SOLD_OUT = "SOLD_OUT";
    public static final String LISTING_STATUS_LISTED = "LISTED";

    // ==================== 回收订单状态常量 ====================
    public static final String RECYCLE_STATUS_CREATED = "CREATED";
    public static final String RECYCLE_STATUS_QUALITY_CHECKED = "QUALITY_CHECKED";
    public static final String RECYCLE_STATUS_PRICE_REVIEWED = "PRICE_REVIEWED";
    public static final String RECYCLE_STATUS_LISTED = "LISTED";

    // ==================== 二销订单支付状态常量 ====================
    public static final String PAY_STATUS_UNPAID = "UNPAID";
    public static final String PAY_STATUS_PAID = "PAID";
    public static final String PAY_STATUS_REFUNDED = "REFUNDED";

    // ==================== 二销订单履约状态常量 ====================
    public static final String FULFILL_STATUS_WAIT_PAY = "WAIT_PAY";
    public static final String FULFILL_STATUS_TO_DELIVER = "TO_DELIVER";
    public static final String FULFILL_STATUS_DELIVERED = "DELIVERED";
    public static final String FULFILL_STATUS_COMPLETED = "COMPLETED";
    public static final String FULFILL_STATUS_CANCELLED = "CANCELLED";
    public static final String FULFILL_STATUS_REFUNDED = "REFUNDED";

    // ==================== 物流状态常量 ====================
    public static final String LOGISTICS_STATUS_TO_SHIP = "TO_SHIP";

    // ==================== 操作审计动作常量 ====================
    public static final String ACTION_PAYMENT_REPLAY_DIAGNOSIS_QUERY = "PAYMENT_REPLAY_DIAGNOSIS_QUERY";
    public static final String ACTION_PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY = "PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY";

    // ==================== 时间常量 ====================
    public static final int DEFAULT_REVIEW_APPEND_WINDOW_DAYS = 30;
    public static final int DEFAULT_REPLAY_HEALTH_PENDING_THRESHOLD = 100;
    public static final int DEFAULT_REPLAY_HEALTH_DEAD_THRESHOLD = 10;
    public static final int DEFAULT_REPLAY_HEALTH_OLDEST_PENDING_MINUTES_THRESHOLD = 30;
    public static final int DEFAULT_REPLAY_AUTO_HANDLE_TRACE_IDEMPOTENT_WINDOW_SECONDS = 30;
    public static final long DEFAULT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN_DURATION_MS = 5000;
    public static final int DEFAULT_REPLAY_HEALTH_CLEANUP_WARN_LOOKBACK_MINUTES = 30;
    public static final double DEFAULT_REVIEW_RISK_LEVEL_HIGH_SENSITIVE_RATE = 0.25;
    public static final double DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_SENSITIVE_RATE = 0.12;
    public static final int DEFAULT_REVIEW_RISK_LEVEL_HIGH_PENDING_REPORTS = 10;
    public static final int DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_PENDING_REPORTS = 5;
    public static final int DEFAULT_REVIEW_RISK_LEVEL_HIGH_TOTAL_REPORTS = 30;
    public static final int DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_TOTAL_REPORTS = 15;
}
