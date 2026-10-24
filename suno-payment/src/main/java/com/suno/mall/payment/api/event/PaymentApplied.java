package com.suno.mall.payment.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/**
 * PAY-E002 v1：支付落账事实（幂等键驱动，迟到支付不会重复发布）。
 */
@UseCaseId("PAY-E002")
@EventVersion(1)
public record PaymentApplied(
        String orderNo,
        String idempotencyKey,
        String payStatus
) implements DocumentedDomainEvent {
}
