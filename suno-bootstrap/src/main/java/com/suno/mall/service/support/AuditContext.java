package com.suno.mall.service.support;

import java.util.Map;

/**
 * 审计上下文：管理端操作可携带请求标识与变更摘要，随审计日志持久化。
 */
public record AuditContext(String requestId, Map<String, Object> changeSummary) {
    public static AuditContext empty() {
        return new AuditContext(null, null);
    }
}
