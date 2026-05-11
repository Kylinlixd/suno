package com.recycle.mall.interfaces.scheduler;

import com.recycle.mall.application.RecycleApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReplayAutoHandleIdempotencyCleanupScheduler {

    private final RecycleApplicationService recycleApplicationService;
    @Value("${payment.callback.replay-auto-handle-idempotency-retain-days:7}")
    private int retainDays;

    public PaymentReplayAutoHandleIdempotencyCleanupScheduler(RecycleApplicationService recycleApplicationService) {
        this.recycleApplicationService = recycleApplicationService;
    }

    @Scheduled(fixedDelayString = "${payment.callback.replay-auto-handle-idempotency-cleanup-fixed-delay-ms:3600000}")
    public void cleanupAutoHandleIdempotencyRecords() {
        recycleApplicationService.cleanupReplayAutoHandleIdempotencyRecords(retainDays);
    }
}
