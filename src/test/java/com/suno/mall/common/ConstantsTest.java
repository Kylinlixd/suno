
package com.suno.mall.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 常量类测试
 */
public class ConstantsTest {

    @Test
    public void testLanguageConstants() {
        // 测试语言常量
        assertEquals("zh-CN", Constants.DEFAULT_LANG);
        assertEquals("zh-CN", Constants.LANG_ZH_CN);
        assertEquals("en-US", Constants.LANG_EN_US);
    }

    @Test
    public void testVersionConstants() {
        // 测试版本常量
        assertEquals("1.0.0", Constants.DIAGNOSIS_SCHEMA_VERSION);
        assertEquals("1.0.0", Constants.CLEANUP_PERFORMANCE_CHECK_SCHEMA_VERSION);
        assertEquals("1.0.0", Constants.HEALTH_SCHEMA_VERSION);
        assertEquals("1.0.0", Constants.QUERY_AUDIT_ACTIONS_SCHEMA_VERSION);
        assertEquals("1.0.0", Constants.STATUS_DICTIONARY_VERSION);
        assertEquals("1.0.0", Constants.GLOBAL_ERROR_CODE_DICT_VERSION);
        assertEquals("1.0.0", Constants.ALERT_NOISE_RULES_VERSION);
        assertEquals("1.0.0", Constants.CONFIG_CENTER_BUNDLE_VERSION);
        assertEquals("1.0.0", Constants.DEGRADE_ACTION_DICT_VERSION);
    }

    @Test
    public void testListingStatusConstants() {
        // 测试转售状态常量
        assertEquals("ON_SHELF", Constants.LISTING_STATUS_ON_SHELF);
        assertEquals("SOLD_OUT", Constants.LISTING_STATUS_SOLD_OUT);
    }

    @Test
    public void testAuditActionConstants() {
        // 测试操作审计动作常量
        assertEquals("PAYMENT_REPLAY_DIAGNOSIS_QUERY", Constants.ACTION_PAYMENT_REPLAY_DIAGNOSIS_QUERY);
        assertEquals("PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY", Constants.ACTION_PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY);
    }

    @Test
    public void testTimeConstants() {
        // 测试时间常量
        assertEquals(30, Constants.DEFAULT_REVIEW_APPEND_WINDOW_DAYS);
        assertEquals(100, Constants.DEFAULT_REPLAY_HEALTH_PENDING_THRESHOLD);
        assertEquals(10, Constants.DEFAULT_REPLAY_HEALTH_DEAD_THRESHOLD);
        assertEquals(30, Constants.DEFAULT_REPLAY_HEALTH_OLDEST_PENDING_MINUTES_THRESHOLD);
        assertEquals(30, Constants.DEFAULT_REPLAY_AUTO_HANDLE_TRACE_IDEMPOTENT_WINDOW_SECONDS);
        assertEquals(5000, Constants.DEFAULT_REPLAY_AUTO_HANDLE_IDEMPOTENCY_CLEANUP_WARN_DURATION_MS);
        assertEquals(30, Constants.DEFAULT_REPLAY_HEALTH_CLEANUP_WARN_LOOKBACK_MINUTES);
    }

    @Test
    public void testRiskConstants() {
        // 测试风险常量
        assertEquals(0.25, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_SENSITIVE_RATE);
        assertEquals(0.12, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_SENSITIVE_RATE);
        assertEquals(10, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_PENDING_REPORTS);
        assertEquals(5, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_PENDING_REPORTS);
        assertEquals(30, Constants.DEFAULT_REVIEW_RISK_LEVEL_HIGH_TOTAL_REPORTS);
        assertEquals(15, Constants.DEFAULT_REVIEW_RISK_LEVEL_MEDIUM_TOTAL_REPORTS);
    }

    @Test
    public void testConstantsClass() {
        // 测试常量类不可实例化
        try {
            java.lang.reflect.Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("应该抛出异常");
        } catch (java.lang.reflect.InaccessibleObjectException e) {
            // 构造函数不可访问，测试通过
            assertTrue(e instanceof java.lang.reflect.InaccessibleObjectException);
        } catch (Exception e) {
            fail("预期抛出 InaccessibleObjectException，但抛出了 " + e.getClass().getName());
        }
    }
}
