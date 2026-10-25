package com.suno.mall.marketplace.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** MKT-E005 v1：履约完成事实（买家确认收货或自动确认）。 */
@UseCaseId("MKT-E005")
@EventVersion(1)
public record MarketplaceFulfillmentCompleted(
        String orderNo,
        Long buyerUserId
) implements DocumentedDomainEvent {
}
