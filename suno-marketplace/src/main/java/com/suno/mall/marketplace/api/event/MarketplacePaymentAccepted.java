package com.suno.mall.marketplace.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** MKT-E003 v1：商城侧支付确认事实（与 PAY-E002 同触发，从商城域视角）。 */
@UseCaseId("MKT-E003")
@EventVersion(1)
public record MarketplacePaymentAccepted(
        String orderNo,
        String idempotencyKey
) implements DocumentedDomainEvent {
}
