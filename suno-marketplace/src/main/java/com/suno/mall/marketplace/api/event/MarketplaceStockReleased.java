package com.suno.mall.marketplace.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** MKT-E004 v1：库存释放事实（取消/超时关单后，同一订单只释放一次）。 */
@UseCaseId("MKT-E004")
@EventVersion(1)
public record MarketplaceStockReleased(
        String orderNo,
        Long listingId,
        String reason
) implements DocumentedDomainEvent {
}
