package com.suno.mall.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * P2 短信通知通道：关键订单事件（退款、发货）在启用时同步短信。
 * 默认关闭（mall.notification.sms-enabled=false）；发送实现为日志 Provider，
 * 生产接短信服务商后替换 send 一处即可。
 */
@Service
public class SmsNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

    private final boolean enabled;

    public SmsNotificationChannel(@Value("${mall.notification.sms-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回是否实际发送（启用且手机号用户）；失败不阻断业务事务 */
    public boolean trySend(String username, String content) {
        if (!enabled || username == null || !username.matches("1\\d{10}")) {
            return false;
        }
        log.info("sms notification to {}****{}: {}", username.substring(0, 3),
                username.substring(username.length() - 2), content);
        return true;
    }
}
