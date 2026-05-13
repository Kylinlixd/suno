package com.suno.mall.controller;

import com.recycle.mall.service.PaymentSignatureService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentNonceCleanupScheduler {

    private final PaymentSignatureService paymentSignatureService;

    public PaymentNonceCleanupScheduler(PaymentSignatureService paymentSignatureService) {
        this.paymentSignatureService = paymentSignatureService;
    }

    @Scheduled(fixedDelayString = "${payment.callback.nonce-cleanup-fixed-delay-ms:300000}")
    public void cleanupExpiredNonces() {
        paymentSignatureService.cleanupExpiredNonces();
    }
}
