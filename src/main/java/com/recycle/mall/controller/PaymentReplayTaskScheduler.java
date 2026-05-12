package com.recycle.mall.controller;

import com.recycle.mall.service.RecycleApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReplayTaskScheduler {

    private final RecycleApplicationService recycleApplicationService;
    private final int consumeBatchSize;

    public PaymentReplayTaskScheduler(
            RecycleApplicationService recycleApplicationService,
            @Value("${payment.callback.replay-consume-batch-size:20}") int consumeBatchSize
    ) {
        this.recycleApplicationService = recycleApplicationService;
        this.consumeBatchSize = consumeBatchSize;
    }

    @Scheduled(fixedDelayString = "${payment.callback.replay-consume-fixed-delay-ms:30000}")
    public void consumeReplayTasks() {
        recycleApplicationService.consumeReplayTasks(consumeBatchSize);
    }
}
