
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

    // ==================== 转售状态常量 ====================
    public static final String LISTING_STATUS_ON_SHELF = "ON_SHELF";
    public static final String LISTING_STATUS_SOLD_OUT = "SOLD_OUT";

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
