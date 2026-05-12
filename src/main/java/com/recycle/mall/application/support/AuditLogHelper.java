
package com.recycle.mall.application.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 审计日志辅助工具
 */
public final class AuditLogHelper {

    private AuditLogHelper() {}

    public static String buildAuditContextSuffix(AuditContext auditContext, ObjectMapper objectMapper) {
        String safeRequestId = auditContext == null || auditContext.requestId() == null
                ? ""
                : auditContext.requestId().trim();
        Map<String, Object> safeChangeSummary = auditContext == null ? null : auditContext.changeSummary();
        return ",requestId=" + safeRequestId + ",changeSummary=" + compactChangeSummary(safeChangeSummary, objectMapper);
    }

    public static String compactChangeSummary(Map<String, Object> changeSummary, ObjectMapper objectMapper) {
        if (changeSummary == null || changeSummary.isEmpty()) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(changeSummary);
            return json.length() > 500 ? json.substring(0, 500) + "..." : json;
        } catch (JsonProcessingException ex) {
            return String.valueOf(changeSummary);
        }
    }
}
