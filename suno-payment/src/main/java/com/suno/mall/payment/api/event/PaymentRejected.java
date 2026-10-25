package com.suno.mall.payment.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** PAY-E003 v1：支付拒绝事实（验签失败/状态冲突/金额不符）。 */
@UseCaseId("PAY-E003")
@EventVersion(1)
public record PaymentRejected(
        String orderNo,
        String reason
) implements DocumentedDomainEvent {
}
