package com.suno.mall.controller;

import com.suno.mall.service.AuditLogService;
import com.suno.mall.service.PaymentReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PAY-S004 支付对账调度器：定期核对「已支付/已退款订单 ↔ 支付成功回调/幂等账本/渠道交易号」，
 * 差异写审计告警。进程崩溃不丢数据（幂等扫描），多实例部署建议主实例开启。
 */
@Component
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationService paymentReconciliationService;

    public PaymentReconciliationScheduler(PaymentReconciliationService paymentReconciliationService) {
        this.paymentReconciliationService = paymentReconciliationService;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay-ms:21600000}")
    public void reconcilePayments() {
        paymentReconciliationService.reconcile();
    }
}
