package com.suno.mall.service.support;

import java.util.Map;

/**
 * 引焆数接发帮加查看计算和力；
 */
public record AuditContext(String requestId, Map<String, Object> changeSummary) {
    public static AuditContext empty() {
        return new AuditContext(null, null);
    }
}
