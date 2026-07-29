
package com.suno.mall.service;

import com.suno.mall.common.Constants;
import com.suno.mall.dao.PaymentCallbackLogRepository;
import com.suno.mall.dao.PaymentReplayTaskRepository;
import com.suno.mall.dao.PaymentReplayAutoHandleIdempotencyRepository;
import com.suno.mall.dao.OperationAuditLogRepository;
import com.suno.mall.dao.ResaleOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付回调重放服务测试
 */
@ExtendWith(MockitoExtension.class)
public class PaymentReplayServiceTest {

    @Mock
    private PaymentCallbackLogRepository paymentCallbackLogRepository;

    @Mock
    private PaymentReplayTaskRepository paymentReplayTaskRepository;

    @Mock
    private PaymentReplayAutoHandleIdempotencyRepository replayAutoHandleIdempotencyRepository;

    @Mock
    private OperationAuditLogRepository operationAuditLogRepository;

    @Mock
    private ResaleOrderRepository resaleOrderRepository;

    @InjectMocks
    private PaymentReplayService paymentReplayService;

    @BeforeEach
    public void setUp() {
        // 验证常量是否正确加载
        assertEquals("1.0.0", Constants.DIAGNOSIS_SCHEMA_VERSION);
        assertEquals("zh-CN", Constants.DEFAULT_LANG);
    }

    @Test
    public void testLogPaymentCallback() {
        // 测试支付回调日志记录功能
        String orderNo = "TEST123456";
        String idempotencyKey = "IDEMPOTENCY123";
        String payStatus = "SUCCESS";
        String nonce = "NONCE123";
        long timestamp = System.currentTimeMillis();
        String signature = "SIGNATURE123";
        String callbackStatus = "PROCESSED";
        String errorMessage = null;
        String responseBody = "{\"status\":\"success\"}";
        String source = "ALIPAY";

        // 调用方法
        paymentReplayService.logPaymentCallback(
                orderNo, idempotencyKey, payStatus, nonce, timestamp,
                signature, callbackStatus, errorMessage, responseBody, source);

        // 验证方法是否被调用
        verify(paymentCallbackLogRepository).save(any());
    }

    @Test
    public void testConstantsInitialization() {
        // 测试常量初始化
        assertEquals("PAYMENT_REPLAY_DIAGNOSIS_QUERY", Constants.ACTION_PAYMENT_REPLAY_DIAGNOSIS_QUERY);
        assertEquals("PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY", Constants.ACTION_PAYMENT_REPLAY_CLEANUP_PERFORMANCE_CHECK_QUERY);
        assertEquals("zh-CN", Constants.LANG_ZH_CN);
        assertEquals("en-US", Constants.LANG_EN_US);
    }
}
