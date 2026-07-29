
package com.suno.mall.service.support;

import java.util.Collection;
import java.util.Map;

/**
 * 统一非空校验工具
 * <p>
 * 提供语义清晰的非空校验方法，替代散落在各 service 中的
 * {@code if (x == null || x.isBlank())} 等重复模式。
 */
public final class ValidationHelper {

    private ValidationHelper() {}

    // ==================== 布尔判断（不抛异常） ====================

    /**
     * 判断字符串是否为 null 或全空白
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 判断字符串是否非 null 且非全空白
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * 判断集合是否为 null 或空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否非 null 且非空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 判断 Map 是否为 null 或空
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断对象是否为 null
     */
    public static boolean isNull(Object value) {
        return value == null;
    }

    /**
     * 判断对象是否非 null
     */
    public static boolean isNotNull(Object value) {
        return value != null;
    }

    // ==================== 断言（抛 IllegalArgumentException） ====================

    /**
     * 断言字符串非 null 且非全空白，否则抛异常
     *
     * @param value   待校验值
     * @param message 异常信息
     * @return 校验后的值（trim 后）
     */
    public static String requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /**
     * 断言对象非 null，否则抛异常
     *
     * @param value   待校验值
     * @param message 异常信息
     * @return 校验后的值
     */
    public static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 断言集合非 null 且非空，否则抛异常
     *
     * @param collection 待校验集合
     * @param message    异常信息
     * @return 校验后的集合
     */
    public static <T extends Collection<?>> T requireNotEmpty(T collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 断言 Map 非 null 且非空，否则抛异常
     *
     * @param map     待校验 Map
     * @param message 异常信息
     * @return 校验后的 Map
     */
    public static <T extends Map<?, ?>> T requireNotEmpty(T map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    // ==================== 安全默认值 ====================

    /**
     * 若字符串为 null 或空白则返回默认值，否则返回 trim 后的值
     */
    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    /**
     * 若对象为 null 则返回默认值
     */
    public static <T> T defaultIfNull(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
