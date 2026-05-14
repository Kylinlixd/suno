
package com.suno.mall;

import com.suno.mall.common.Constants;
import com.suno.mall.common.DatabaseOptimizationHelper;
import com.suno.mall.service.ConfigCenterService;
import com.suno.mall.service.PaymentReplayService;
import com.suno.mall.service.ResaleOrderService;
import com.suno.mall.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试运行器 - 集中运行所有测试
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestRunner {

    @Autowired
    private ConfigCenterService configCenterService;

    @Autowired
    private ResaleOrderService resaleOrderService;

    @Autowired
    private PaymentReplayService paymentReplayService;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    @DisplayName("测试常量初始化")
    public void testConstantsInitialization() {
        // 测试常量初始化
        assertEquals("1.0.0", Constants.DIAGNOSIS_SCHEMA_VERSION);
        assertEquals("zh-CN", Constants.DEFAULT_LANG);
        assertEquals("ON_SHELF", Constants.LISTING_STATUS_ON_SHELF);
        assertEquals("SOLD_OUT", Constants.LISTING_STATUS_SOLD_OUT);
        assertEquals(30, Constants.DEFAULT_REVIEW_APPEND_WINDOW_DAYS);
        assertEquals(0.25, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_SENSITIVE_RATE);
        assertEquals(100, Constants.DEFAULT_REPLAY_HEALTH_PENDING_THRESHOLD);
        assertEquals(10, Constants.DEFAULT_REPLAY_HEALTH_DEAD_THRESHOLD);
    }

    @Test
    @DisplayName("测试配置中心服务")
    public void testConfigCenterService() {
        // 测试配置中心服务
        Map<String, Object> globalErrorCodeDict = configCenterService.getGlobalErrorCodeDict();
        assertNotNull(globalErrorCodeDict);
        assertEquals("1.0.0", globalErrorCodeDict.get("version"));

        Map<String, Object> alertNoiseRules = configCenterService.getAlertNoiseRules();
        assertNotNull(alertNoiseRules);
        assertEquals("1.0.0", alertNoiseRules.get("version"));

        Map<String, Object> configBundle = configCenterService.getConfigCenterBundle();
        assertNotNull(configBundle);
        assertEquals("1.0.0", configBundle.get("version"));
    }

    @Test
    @DisplayName("测试转售订单服务")
    public void testResaleOrderService() {
        // 测试转售订单服务
        assertNotNull(resaleOrderService);
        assertEquals(30, Constants.DEFAULT_REVIEW_APPEND_WINDOW_DAYS);
        assertEquals(0.25, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_SENSITIVE_RATE);
    }

    @Test
    @DisplayName("测试支付回调重放服务")
    public void testPaymentReplayService() {
        // 测试支付回调重放服务
        assertNotNull(paymentReplayService);
        assertEquals(100, Constants.DEFAULT_REPLAY_HEALTH_PENDING_THRESHOLD);
        assertEquals(10, Constants.DEFAULT_REPLAY_HEALTH_DEAD_THRESHOLD);
        assertEquals(30, Constants.DEFAULT_REPLAY_HEALTH_OLDEST_PENDING_MINUTES_THRESHOLD);
    }

    @Test
    @DisplayName("测试全局异常处理器")
    public void testGlobalExceptionHandler() {
        // 测试全局异常处理器
        assertNotNull(globalExceptionHandler);
        assertEquals("DATA_INTEGRITY_VIOLATION", com.suno.mall.common.ErrorCode.DATA_INTEGRITY_VIOLATION);
        assertEquals("SYS_DATABASE_ERROR", com.suno.mall.common.ErrorCode.SYS_DATABASE_ERROR);
    }

    @Test
    @DisplayName("测试数据库优化工具")
    public void testDatabaseOptimizationHelper() {
        // 测试数据库优化工具
        assertNotNull(DatabaseOptimizationHelper.class);

        // 测试创建分页对象
        org.springframework.data.domain.Pageable pageable = DatabaseOptimizationHelper.createPageable(0, 10, 
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
    }

    @Test
    @DisplayName("测试系统整体流程")
    public void testSystemWorkflow() {
        // 测试系统整体流程
        // 1. 配置中心加载配置
        Map<String, Object> config = configCenterService.getConfigCenterBundle();
        assertNotNull(config);

        // 2. 支付回调重放服务初始化
        assertNotNull(paymentReplayService);

        // 3. 转售订单服务初始化
        assertNotNull(resaleOrderService);

        // 4. 全局异常处理器初始化
        assertNotNull(globalExceptionHandler);

        // 5. 常量初始化
        assertEquals("1.0.0", Constants.DIAGNOSIS_SCHEMA_VERSION);
        assertEquals("zh-CN", Constants.DEFAULT_LANG);
    }
}
