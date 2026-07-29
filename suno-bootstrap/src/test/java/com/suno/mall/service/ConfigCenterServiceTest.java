
package com.suno.mall.service;

import com.suno.mall.common.Constants;
import com.suno.mall.service.support.AuditContext;
import com.suno.mall.service.support.VersionHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 配置中心服务测试
 */
@ExtendWith(MockitoExtension.class)
public class ConfigCenterServiceTest {

    @Mock
    private AuditContext auditContext;

    @Mock
    private VersionHelper versionHelper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ResaleReviewService resaleReviewService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ConfigCenterService configCenterService;

    @BeforeEach
    public void setUp() {
        // 验证常量是否正确加载
        assertEquals("1.0.0", Constants.GLOBAL_ERROR_CODE_DICT_VERSION);
        assertEquals("1.0.0", Constants.ALERT_NOISE_RULES_VERSION);
        assertEquals("1.0.0", Constants.CONFIG_CENTER_BUNDLE_VERSION);
        assertEquals("1.0.0", Constants.DEGRADE_ACTION_DICT_VERSION);
        assertEquals(0.25, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_SENSITIVE_RATE);
        assertEquals(0.12, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_SENSITIVE_RATE);
        assertEquals(10, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_PENDING_REPORTS);
        assertEquals(5, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_PENDING_REPORTS);
        assertEquals(30, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_TOTAL_REPORTS);
        assertEquals(15, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_TOTAL_REPORTS);
    }

    @Test
    public void testGetGlobalErrorCodeDict() {
        // 测试获取全局错误码字典
        Map<String, Object> dict = configCenterService.adminGlobalErrorCodeDictionary();

        assertNotNull(dict);
        assertEquals("1.0.0", dict.get("version"));
        assertTrue(dict.containsKey("items"));
    }

    @Test
    public void testGetAlertNoiseRules() {
        // 测试获取告警降噪规则
        Map<String, Object> rules = configCenterService.getAlertNoiseRules();

        assertNotNull(rules);
        assertEquals("1.0.0", rules.get("version"));
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) rules.get("rules");
        assertTrue(values.containsKey("allowlistKeys"));
        assertTrue(values.containsKey("denylistKeys"));
    }

    @Test
    public void testGetConfigCenterBundle() throws JsonProcessingException {
        // 测试获取配置中心包
        when(resaleReviewService.adminGetReviewStrategyConfig()).thenReturn(Map.of(
                "version", "1.0.0", "updatedAt", LocalDateTime.of(2026, 4, 29, 10, 0)
        ));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> bundle = configCenterService.adminConfigCenterBundle();

        assertNotNull(bundle);
        assertEquals("1.0.0", bundle.get("version"));
        assertTrue(bundle.containsKey("modules"));
    }

    @Test
    public void testGetDegradeActionDict() {
        // 测试获取降级行动字典
        Map<String, Object> dict = configCenterService.adminDegradeActionTypeDictionary();

        assertNotNull(dict);
        assertEquals("1.0.0", dict.get("version"));
        assertTrue(dict.containsKey("items"));
    }

    @Test
    public void testConstantsInitialization() {
        // 测试常量初始化
        assertEquals("1.0.0", Constants.GLOBAL_ERROR_CODE_DICT_VERSION);
        assertEquals("1.0.0", Constants.ALERT_NOISE_RULES_VERSION);
        assertEquals("1.0.0", Constants.CONFIG_CENTER_BUNDLE_VERSION);
        assertEquals("1.0.0", Constants.DEGRADE_ACTION_DICT_VERSION);
        assertEquals(0.25, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_SENSITIVE_RATE);
        assertEquals(0.12, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_SENSITIVE_RATE);
        assertEquals(10, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_PENDING_REPORTS);
        assertEquals(5, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_PENDING_REPORTS);
        assertEquals(30, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_TOTAL_REPORTS);
        assertEquals(15, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_TOTAL_REPORTS);
    }
}
