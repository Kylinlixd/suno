
package com.recycle.mall.service.support;

/**
 * 审计上下文，用于传递请求级审计信息
 */
public record AuditContext(String requestId, java.util.Map<String, Object> changeSummary) {
    public static AuditContext empty() {
        return new AuditContext(null, null);
    }
}
