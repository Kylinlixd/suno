
package com.suno.mall.service.support;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 国际化辅助工具
 */
public final class I18nHelper {

    public static final String LANG_ZH_CN = "zh-CN";
    public static final String LANG_EN_US = "en-US";
    public static final String DEFAULT_LANG = LANG_ZH_CN;

    private I18nHelper() {}

    public static String normalizeLang(@Nullable String lang) {
        if (lang == null || lang.isBlank()) {
            return DEFAULT_LANG;
        }
        String normalized = lang.trim();
        if (LANG_EN_US.equalsIgnoreCase(normalized)) {
            return LANG_EN_US;
        }
        return LANG_ZH_CN;
    }

    public static List<String> supportedLangs() {
        return List.of(LANG_ZH_CN, LANG_EN_US);
    }

    public static Map<String, String> zhEn(String zh, String en) {
        return Map.of(LANG_ZH_CN, zh, LANG_EN_US, en);
    }

    // ---- 支付状态 ----
    public static Map<String, String> payStatusLabelI18n(@Nullable String status) {
        return switch (status == null ? "" : status) {
            case "UNPAID" -> zhEn("未支付", "Unpaid");
            case "PAID" -> zhEn("已支付", "Paid");
            case "REFUNDED" -> zhEn("已退款", "Refunded");
            default -> zhEn("未知状态", "Unknown");
        };
    }

    // ---- 履约状态 ----
    public static Map<String, String> fulfillStatusLabelI18n(@Nullable String status) {
        return switch (status == null ? "" : status) {
            case "WAIT_PAY" -> zhEn("待支付", "Pending Payment");
            case "TO_DELIVER" -> zhEn("待发货", "Pending Shipment");
            case "DELIVERED" -> zhEn("待收货", "Awaiting Receipt");
            case "COMPLETED" -> zhEn("已完成", "Completed");
            case "CANCELLED" -> zhEn("已取消", "Cancelled");
            case "REFUNDED" -> zhEn("已退款", "Refunded");
            default -> zhEn("未知状态", "Unknown");
        };
    }

    // ---- 订单状态文本 ----
    public static String orderStatusText(@Nullable String payStatus, String fulfillStatus) {
        if ("REFUNDED".equals(payStatus) || "REFUNDED".equals(fulfillStatus)) {
            return "已退款";
        }
        return switch (fulfillStatus) {
            case "WAIT_PAY" -> "待支付";
            case "TO_DELIVER" -> "待发货";
            case "DELIVERED" -> "待收货";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> "状态更新中";
        };
    }

    public static Map<String, String> orderStatusTextI18n(@Nullable String payStatus, String fulfillStatus) {
        if ("REFUNDED".equals(payStatus) || "REFUNDED".equals(fulfillStatus)) {
            return zhEn("已退款", "Refunded");
        }
        return switch (fulfillStatus) {
            case "WAIT_PAY" -> zhEn("待支付", "Pending Payment");
            case "TO_DELIVER" -> zhEn("待发货", "Pending Shipment");
            case "DELIVERED" -> zhEn("待收货", "Awaiting Receipt");
            case "COMPLETED" -> zhEn("已完成", "Completed");
            case "CANCELLED" -> zhEn("已取消", "Cancelled");
            default -> zhEn("状态更新中", "Status Updating");
        };
    }

    // ---- 健康等级 ----
    public static Map<String, String> healthLevelI18n(@Nullable String level) {
        return switch (level) {
            case "GOOD" -> zhEn("优", "Good");
            case "NORMAL" -> zhEn("中", "Normal");
            case "ATTENTION" -> zhEn("需关注", "Needs Attention");
            default -> zhEn("中性", "Neutral");
        };
    }

    // ---- 追评剩余文本 ----
    public static String appendRemainingText(long remainingHours, long remainingDays) {
        if (remainingHours <= 0) {
            return "追评窗口已结束";
        }
        long restHours = remainingHours % 24;
        if (remainingDays <= 0) {
            return "还可追评 " + remainingHours + " 小时";
        }
        if (restHours == 0) {
            return "还可追评 " + remainingDays + " 天";
        }
        return "还可追评 " + remainingDays + " 天 " + restHours + " 小时";
    }

    public static Map<String, String> appendRemainingTextI18n(long remainingHours, long remainingDays) {
        String zh = appendRemainingText(remainingHours, remainingDays);
        String en;
        if (remainingHours <= 0) {
            en = "Append-review window has ended";
        } else {
            long restHours = remainingHours % 24;
            if (remainingDays <= 0) {
                en = "Append review available for " + remainingHours + " hour(s)";
            } else if (restHours == 0) {
                en = "Append review available for " + remainingDays + " day(s)";
            } else {
                en = "Append review available for " + remainingDays + " day(s) " + restHours + " hour(s)";
            }
        }
        return Map.of(LANG_ZH_CN, zh, LANG_EN_US, en);
    }

    // ---- 趋势标签 ----
    public static Map<String, String> trendLabelI18n(@Nullable String trend) {
        return switch (trend) {
            case "UP" -> zhEn("上升", "Up");
            case "DOWN" -> zhEn("下降", "Down");
            default -> zhEn("持平", "Flat");
        };
    }

    // ---- Delta 文本 ----
    public static Map<String, String> deltaTextI18n(java.math.BigDecimal delta, String zhMetric, String enMetric) {
        String symbol = delta.compareTo(java.math.BigDecimal.ZERO) > 0 ? "+" : "";
        String value = symbol + delta.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return Map.of(LANG_ZH_CN, zhMetric + " 变化 " + value, LANG_EN_US, enMetric + " delta " + value);
    }
}
