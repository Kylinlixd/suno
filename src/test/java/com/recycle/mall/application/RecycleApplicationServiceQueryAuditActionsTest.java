package com.recycle.mall.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.mall.domain.service.AiAuditService;
import com.recycle.mall.domain.service.LogisticsService;
import com.recycle.mall.domain.service.PointsService;
import com.recycle.mall.domain.service.SnParseService;
import com.recycle.mall.domain.service.ValuationService;
import com.recycle.mall.infrastructure.repository.LogisticsTrackRepository;
import com.recycle.mall.infrastructure.repository.OperationAuditLogRepository;
import com.recycle.mall.infrastructure.repository.PaymentCallbackLogRepository;
import com.recycle.mall.infrastructure.repository.PaymentIdempotencyRepository;
import com.recycle.mall.infrastructure.repository.PaymentReplayAutoHandleIdempotencyRepository;
import com.recycle.mall.infrastructure.repository.PaymentReplayTaskRepository;
import com.recycle.mall.infrastructure.repository.PointsLedgerRepository;
import com.recycle.mall.infrastructure.repository.ProductRepository;
import com.recycle.mall.infrastructure.repository.RecycleOrderRepository;
import com.recycle.mall.infrastructure.repository.ResaleListingRepository;
import com.recycle.mall.infrastructure.repository.ResaleOrderRepository;
import com.recycle.mall.infrastructure.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecycleApplicationServiceQueryAuditActionsTest {

    @Test
    void shouldReturnChineseWhenLangIsZhCn() {
        RecycleApplicationService service = createService();

        Map<String, Object> result = service.replayQueryAuditActions("trace-test-zh", "zh-CN");

        assertEquals("zh-CN", result.get("lang"));
        assertEquals("zh-CN", result.get("requestedLang"));
        assertFalse((Boolean) result.get("langFallbackApplied"));
        assertEquals("NONE", result.get("langFallbackReason"));

        @SuppressWarnings("unchecked")
        Map<String, String> descDictionary = (Map<String, String>) result.get("descDictionary");
        assertEquals("健康查询", descDictionary.get("queryAudit.health"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertEquals(3, ((Number) summary.get("totalCount")).intValue());
        assertEquals(100, ((Number) summary.get("activeRate")).intValue());
    }

    @Test
    void shouldReturnEnglishWhenLangIsEnUs() {
        RecycleApplicationService service = createService();

        Map<String, Object> result = service.replayQueryAuditActions("trace-test-en", "en-US");

        assertEquals("en-US", result.get("lang"));
        assertEquals("en-US", result.get("requestedLang"));
        assertFalse((Boolean) result.get("langFallbackApplied"));
        assertEquals("NONE", result.get("langFallbackReason"));

        @SuppressWarnings("unchecked")
        Map<String, String> statusDictionary = (Map<String, String>) result.get("statusDictionary");
        assertEquals("Active action", statusDictionary.get("ACTIVE"));

        @SuppressWarnings("unchecked")
        Map<String, Object> compatibility = (Map<String, Object>) result.get("compatibility");
        assertEquals("new clients should read from meta first", compatibility.get("migrationHint"));
        @SuppressWarnings("unchecked")
        Map<String, String> migrationStatusDictionary = (Map<String, String>) compatibility.get("migrationStatusDictionary");
        assertEquals("Structure is stable, no migration required", migrationStatusDictionary.get("STABLE"));
        assertEquals("1.0.0", result.get("requestSpecVersion"));
    }

    @Test
    void shouldFallbackToDefaultLangWhenUnsupportedLangProvidedAndKeepStructureConsistent() {
        RecycleApplicationService service = createService();

        Map<String, Object> result = service.replayQueryAuditActions("trace-test-fr", "fr-FR");

        assertEquals("fr-FR", result.get("requestedLang"));
        assertEquals("zh-CN", result.get("lang"));
        assertTrue((Boolean) result.get("langFallbackApplied"));
        assertEquals("UNSUPPORTED_LANG", result.get("langFallbackReason"));
        assertEquals("meta", result.get("preferredReadPath"));
        assertEquals("1.0.0", result.get("requestSpecVersion"));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> topSummary = (Map<String, Object>) result.get("summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> metaSummary = (Map<String, Object>) meta.get("summary");
        assertEquals(topSummary, metaSummary);

        @SuppressWarnings("unchecked")
        Map<String, Object> topCompatibility = (Map<String, Object>) result.get("compatibility");
        @SuppressWarnings("unchecked")
        Map<String, Object> metaCompatibility = (Map<String, Object>) meta.get("compatibility");
        assertEquals(topCompatibility, metaCompatibility);
        assertEquals("新接入客户端建议优先读取 meta", topCompatibility.get("migrationHint"));

        @SuppressWarnings("unchecked")
        Map<String, String> topDictionaryVersions = (Map<String, String>) result.get("dictionaryVersions");
        @SuppressWarnings("unchecked")
        Map<String, String> metaDictionaryVersions = (Map<String, String>) meta.get("dictionaryVersions");
        assertEquals(topDictionaryVersions, metaDictionaryVersions);

        @SuppressWarnings("unchecked")
        Map<String, Object> topRequestSpec = (Map<String, Object>) result.get("requestSpec");
        @SuppressWarnings("unchecked")
        Map<String, Object> metaRequestSpec = (Map<String, Object>) meta.get("requestSpec");
        assertEquals(topRequestSpec, metaRequestSpec);
        assertEquals("1.0.0", meta.get("requestSpecVersion"));

        @SuppressWarnings("unchecked")
        List<String> supportedFallbackReasons = (List<String>) result.get("supportedLangFallbackReasons");
        assertTrue(supportedFallbackReasons.contains("UNSUPPORTED_LANG"));
    }

    @Test
    void shouldKeepContractShapeForQueryAuditActionsResponse() {
        RecycleApplicationService service = createService();

        Map<String, Object> result = service.replayQueryAuditActions("trace-contract", "zh-CN");

        assertNotNull(result.get("queryAuditActionsSchemaVersion"));
        assertNotNull(result.get("requestId"));
        assertNotNull(result.get("generatedAt"));
        assertNotNull(result.get("meta"));
        assertNotNull(result.get("summary"));
        assertNotNull(result.get("compatibility"));
        assertNotNull(result.get("requestSpec"));
        assertNotNull(result.get("requestSpecVersion"));
        assertNotNull(result.get("dictionaryVersions"));
        assertNotNull(result.get("statusDictionary"));
        assertNotNull(result.get("descDictionary"));
        assertNotNull(result.get("langFallbackReasonDictionary"));
        assertNotNull(result.get("actions"));

        assertInstanceOf(Map.class, result.get("meta"));
        assertInstanceOf(Map.class, result.get("summary"));
        assertInstanceOf(Map.class, result.get("compatibility"));
        assertInstanceOf(Map.class, result.get("requestSpec"));
        assertInstanceOf(Map.class, result.get("dictionaryVersions"));
        assertInstanceOf(Map.class, result.get("statusDictionary"));
        assertInstanceOf(Map.class, result.get("descDictionary"));
        assertInstanceOf(Map.class, result.get("langFallbackReasonDictionary"));
        assertInstanceOf(List.class, result.get("actions"));

        @SuppressWarnings("unchecked")
        Map<String, Object> compatibility = (Map<String, Object>) result.get("compatibility");
        assertNotNull(compatibility.get("migrationStatus"));
        assertNotNull(compatibility.get("migrationHintKey"));
        assertNotNull(compatibility.get("migrationHint"));
        assertNotNull(compatibility.get("migrationHintDictionary"));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        assertNotNull(meta.get("schemaVersion"));
        assertNotNull(meta.get("summary"));
        assertNotNull(meta.get("compatibility"));
        assertNotNull(meta.get("requestSpec"));
        assertNotNull(meta.get("requestSpecVersion"));

        assertEquals(result.get("requestId"), meta.get("requestId"));
        assertEquals(result.get("generatedAt"), meta.get("generatedAt"));
        assertEquals(result.get("requestedLang"), meta.get("requestedLang"));
        assertEquals(result.get("lang"), meta.get("lang"));
        assertEquals(result.get("langFallbackApplied"), meta.get("langFallbackApplied"));
        assertEquals(result.get("langFallbackReason"), meta.get("langFallbackReason"));
        assertEquals(result.get("defaultLang"), meta.get("defaultLang"));
        assertEquals(result.get("supportedLangs"), meta.get("supportedLangs"));
        assertEquals(result.get("supportedLangFallbackReasons"), meta.get("supportedLangFallbackReasons"));
        assertEquals(result.get("requestSpecVersion"), meta.get("requestSpecVersion"));
        assertEquals("meta", result.get("preferredReadPath"));
        assertEquals(result.get("requestSpec"), meta.get("requestSpec"));
        assertEquals(result.get("compatibility"), meta.get("compatibility"));

        @SuppressWarnings("unchecked")
        Map<String, Object> requestSpec = (Map<String, Object>) result.get("requestSpec");
        @SuppressWarnings("unchecked")
        Map<String, Object> headersSpec = (Map<String, Object>) requestSpec.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> traceIdSpec = (Map<String, Object>) headersSpec.get("X-Trace-Id");
        assertEquals(false, traceIdSpec.get("required"));
        assertNotNull(traceIdSpec.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> querySpec = (Map<String, Object>) requestSpec.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> langSpec = (Map<String, Object>) querySpec.get("lang");
        assertEquals(false, langSpec.get("required"));
        assertEquals("zh-CN", langSpec.get("default"));
        @SuppressWarnings("unchecked")
        List<String> supportedValues = (List<String>) langSpec.get("supportedValues");
        assertTrue(supportedValues.contains("zh-CN"));
        assertTrue(supportedValues.contains("en-US"));
        String fallbackReasonIfUnsupported = String.valueOf(langSpec.get("fallbackReasonIfUnsupported"));
        assertEquals("UNSUPPORTED_LANG", fallbackReasonIfUnsupported);

        @SuppressWarnings("unchecked")
        List<String> topSupportedLangs = (List<String>) result.get("supportedLangs");
        assertEquals(topSupportedLangs, supportedValues);

        @SuppressWarnings("unchecked")
        List<String> topSupportedFallbackReasons = (List<String>) result.get("supportedLangFallbackReasons");
        assertTrue(topSupportedFallbackReasons.contains(fallbackReasonIfUnsupported));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) result.get("actions");
        assertFalse(actions.isEmpty());
        Map<String, Object> firstAction = actions.get(0);
        assertNotNull(firstAction.get("actionType"));
        assertNotNull(firstAction.get("descKey"));
        assertNotNull(firstAction.get("description"));
        assertNotNull(firstAction.get("status"));
        assertInstanceOf(String.class, firstAction.get("actionType"));
        assertInstanceOf(String.class, firstAction.get("descKey"));
        assertInstanceOf(String.class, firstAction.get("description"));
        assertInstanceOf(String.class, firstAction.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        int totalCount = ((Number) summary.get("totalCount")).intValue();
        int activeCount = ((Number) summary.get("activeCount")).intValue();
        int inactiveCount = ((Number) summary.get("inactiveCount")).intValue();
        int activeRate = ((Number) summary.get("activeRate")).intValue();

        long calculatedActiveCount = actions.stream()
                .filter(item -> "ACTIVE".equals(item.get("status")))
                .count();
        assertEquals(actions.size(), totalCount);
        assertEquals(calculatedActiveCount, activeCount);
        assertEquals(totalCount - activeCount, inactiveCount);

        int calculatedActiveRate = totalCount == 0
                ? 0
                : (int) Math.round((activeCount * 100.0) / totalCount);
        assertEquals(calculatedActiveRate, activeRate);

        @SuppressWarnings("unchecked")
        Map<String, String> descDictionary = (Map<String, String>) result.get("descDictionary");
        for (Map<String, Object> action : actions) {
            String descKey = String.valueOf(action.get("descKey"));
            assertTrue(descDictionary.containsKey(descKey));
            assertNotNull(descDictionary.get(descKey));
        }

        @SuppressWarnings("unchecked")
        List<String> supportedFallbackReasons = (List<String>) result.get("supportedLangFallbackReasons");
        String currentFallbackReason = String.valueOf(result.get("langFallbackReason"));
        assertTrue(supportedFallbackReasons.contains(currentFallbackReason));

        @SuppressWarnings("unchecked")
        Map<String, String> fallbackReasonDictionary = (Map<String, String>) result.get("langFallbackReasonDictionary");
        for (String reason : supportedFallbackReasons) {
            assertTrue(fallbackReasonDictionary.containsKey(reason));
            assertNotNull(fallbackReasonDictionary.get(reason));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> compatibility = (Map<String, Object>) result.get("compatibility");
        String migrationStatus = String.valueOf(compatibility.get("migrationStatus"));
        @SuppressWarnings("unchecked")
        List<String> supportedMigrationStatuses = (List<String>) compatibility.get("supportedMigrationStatuses");
        assertTrue(supportedMigrationStatuses.contains(migrationStatus));

        @SuppressWarnings("unchecked")
        Map<String, String> migrationStatusDictionary = (Map<String, String>) compatibility.get("migrationStatusDictionary");
        for (String status : supportedMigrationStatuses) {
            assertTrue(migrationStatusDictionary.containsKey(status));
            assertNotNull(migrationStatusDictionary.get(status));
        }

        String migrationHintKey = String.valueOf(compatibility.get("migrationHintKey"));
        @SuppressWarnings("unchecked")
        Map<String, String> migrationHintDictionary = (Map<String, String>) compatibility.get("migrationHintDictionary");
        assertTrue(migrationHintDictionary.containsKey(migrationHintKey));
        assertEquals(migrationHintDictionary.get(migrationHintKey), compatibility.get("migrationHint"));
    }

    private RecycleApplicationService createService() {
        return new RecycleApplicationService(
                Mockito.mock(AiAuditService.class),
                Mockito.mock(SnParseService.class),
                Mockito.mock(ValuationService.class),
                Mockito.mock(LogisticsService.class),
                Mockito.mock(PointsService.class),
                Mockito.mock(UserAccountRepository.class),
                Mockito.mock(ProductRepository.class),
                Mockito.mock(RecycleOrderRepository.class),
                Mockito.mock(LogisticsTrackRepository.class),
                Mockito.mock(PointsLedgerRepository.class),
                Mockito.mock(ResaleListingRepository.class),
                Mockito.mock(ResaleOrderRepository.class),
                Mockito.mock(OperationAuditLogRepository.class),
                Mockito.mock(PaymentCallbackLogRepository.class),
                Mockito.mock(PaymentIdempotencyRepository.class),
                Mockito.mock(PaymentReplayTaskRepository.class),
                Mockito.mock(PaymentReplayAutoHandleIdempotencyRepository.class),
                new ObjectMapper()
        );
    }
}
