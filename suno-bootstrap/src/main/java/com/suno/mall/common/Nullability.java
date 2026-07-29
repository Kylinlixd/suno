package com.suno.mall.common;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Nullability 注解统一入口
 * <p>
 * 项目中所有需要标注 null 语义的地方，统一使用 JSpecify 的 {@link NonNull} 和 {@link Nullable}。
 * <ul>
 *   <li>方法参数默认视为 {@code @NonNull}（除非显式标注 {@code @Nullable}）</li>
 *   <li>返回值默认视为 {@code @Nullable}（除非显式标注 {@code @NonNull}）</li>
 *   <li>JPA Entity 的 getter 返回值：数据库 nullable=true 的字段标注 {@code @Nullable}</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 *   public Map&lt;String, Object&gt; listOrders(
 *       @NonNull String status,
 *       @Nullable String sortBy
 *   ) { ... }
 *
 *   &#64;Nullable
 *   public String getAppendContent() { return appendContent; }
 * </pre>
 *
 * @see NonNull
 * @see Nullable
 */
public final class Nullability {

    private Nullability() {}
}
