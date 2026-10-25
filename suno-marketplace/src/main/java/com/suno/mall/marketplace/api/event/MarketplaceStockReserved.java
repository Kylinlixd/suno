package com.suno.mall.marketplace.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** MKT-E001 v1：库存预留事实（下单原子扣减，同一订单只预留一次）。 */
@UseCaseId("MKT-E001")
@EventVersion(1)
public record MarketplaceStockReserved(
        String orderNo,
        Long listingId,
        int quantity
) implements DocumentedDomainEvent {
}
