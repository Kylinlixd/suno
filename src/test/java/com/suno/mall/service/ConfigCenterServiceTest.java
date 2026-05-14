
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
        Map<String, Object> dict = configCenterService.getGlobalErrorCodeDict();

        assertNotNull(dict);
        assertEquals("1.0.0", dict.get("version"));
        assertTrue(dict.containsKey("errorCodes"));
    }

    @Test
    public void testGetAlertNoiseRules() {
        // 测试获取告警降噪规则
        Map<String, Object> rules = configCenterService.getAlertNoiseRules();

        assertNotNull(rules);
        assertEquals("1.0.0", rules.get("version"));
        assertTrue(rules.containsKey("allowlistKeys"));
        assertTrue(rules.containsKey("denylistKeys"));
    }

    @Test
    public void testGetConfigCenterBundle() {
        // 测试获取配置中心包
        Map<String, Object> bundle = configCenterService.getConfigCenterBundle();

        assertNotNull(bundle);
        assertEquals("1.0.0", bundle.get("version"));
        assertTrue(bundle.containsKey("globalErrorCodeDict"));
        assertTrue(bundle.containsKey("alertNoiseRules"));
    }

    @Test
    public void testGetDegradeActionDict() {
        // 测试获取降级行动字典
        Map<String, Object> dict = configCenterService.getDegradeActionDict();

        assertNotNull(dict);
        assertEquals("1.0.0", dict.get("version"));
        assertTrue(dict.containsKey("actions"));
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
