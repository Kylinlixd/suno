package com.recycle.mall.controller;

import com.recycle.mall.service.RecycleApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ResaleOrderScheduler {

    private final RecycleApplicationService recycleApplicationService;
    private final int autoCloseExpireMinutes;
    private final int autoCloseBatchSize;
    private final int autoConfirmAfterMinutes;
    private final int autoConfirmBatchSize;

    public ResaleOrderScheduler(
            RecycleApplicationService recycleApplicationService,
            @Value("${mall.order.auto-close-expire-minutes:15}") int autoCloseExpireMinutes,
            @Value("${mall.order.auto-close-batch-size:200}") int autoCloseBatchSize,
            @Value("${mall.order.auto-confirm-receipt-after-minutes:4320}") int autoConfirmAfterMinutes,
            @Value("${mall.order.auto-confirm-receipt-batch-size:200}") int autoConfirmBatchSize
    ) {
        this.recycleApplicationService = recycleApplicationService;
        this.autoCloseExpireMinutes = autoCloseExpireMinutes;
        this.autoCloseBatchSize = autoCloseBatchSize;
        this.autoConfirmAfterMinutes = autoConfirmAfterMinutes;
        this.autoConfirmBatchSize = autoConfirmBatchSize;
    }

    @Scheduled(fixedDelayString = "${mall.order.auto-close-fixed-delay-ms:60000}")
    public void autoCloseExpiredUnpaidOrders() {
        recycleApplicationService.autoCloseExpiredUnpaidOrders(autoCloseExpireMinutes, autoCloseBatchSize);
    }

    @Scheduled(fixedDelayString = "${mall.order.auto-confirm-receipt-fixed-delay-ms:60000}")
    public void autoConfirmDeliveredOrders() {
        recycleApplicationService.autoConfirmDeliveredOrders(autoConfirmAfterMinutes, autoConfirmBatchSize);
    }
}
