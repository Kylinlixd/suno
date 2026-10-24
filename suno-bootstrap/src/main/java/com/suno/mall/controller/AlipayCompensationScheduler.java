package com.suno.mall.controller;

import com.suno.mall.payment.AlipayProperties;
import com.suno.mall.service.RecycleApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PAY-S005 支付宝查单补偿：异步通知丢失时主动向网关核对（trade.query），
 * 查得 TRADE_SUCCESS 的迟到支付以 alipay-query:{tradeNo} 幂等落账。
 * 渠道未启用时跳过（记录 skip 日志），不影响 mock 网关。
 */
@Component
public class AlipayCompensationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlipayCompensationScheduler.class);

    private final AlipayProperties alipayProperties;
    private final com.suno.mall.payment.AlipayOpenApiClient alipayOpenApiClient;
    private final RecycleApplicationService recycleApplicationService;

    public AlipayCompensationScheduler(AlipayProperties alipayProperties,
                                       com.suno.mall.payment.AlipayOpenApiClient alipayOpenApiClient,
                                       RecycleApplicationService recycleApplicationService) {
        this.alipayProperties = alipayProperties;
        this.alipayOpenApiClient = alipayOpenApiClient;
        this.recycleApplicationService = recycleApplicationService;
    }

    @Scheduled(fixedDelayString = "${payment.alipay.query-compensation-fixed-delay-ms:300000}")
    public void compensateMissedNotifies() {
        if (!alipayProperties.isEnabled()) {
            return;
        }
        try {
            int compensated = recycleApplicationService.compensateAlipayPayments(alipayOpenApiClient);
            if (compensated > 0) {
                log.info("支付宝查单补偿落账 {} 笔", compensated);
            }
        } catch (Exception failure) {
            log.warn("支付宝查单补偿执行失败，等待下轮重试: {}", failure.getMessage());
        }
    }
}
