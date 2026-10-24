package com.suno.mall.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;

/**
 * 支付宝渠道启用时的启动期完整性校验：缺密钥直接失败，避免带病上线。
 */
@Configuration
@ConditionalOnProperty(prefix = "payment.alipay", name = "enabled", havingValue = "true")
public class AlipayConfiguration implements InitializingBean {

    private final AlipayProperties alipayProperties;

    public AlipayConfiguration(AlipayProperties alipayProperties) {
        this.alipayProperties = alipayProperties;
    }

    @Override
    public void afterPropertiesSet() {
        String missing = alipayProperties.missingField();
        if (missing != null) {
            throw new IllegalStateException("支付宝渠道已启用但缺少配置: " + missing);
        }
    }
}
