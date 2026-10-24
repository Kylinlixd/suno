package com.suno.mall.marketplace.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/**
 * MKT-E002 v1：二销订单创建事实。买家侧由 JWT 确定，金额为应付口径（含优惠后）。
 */
@UseCaseId("MKT-E002")
@EventVersion(1)
public record MarketplaceOrderCreated(
        String orderNo,
        Long buyerUserId,
        String amount
) implements DocumentedDomainEvent {
}
