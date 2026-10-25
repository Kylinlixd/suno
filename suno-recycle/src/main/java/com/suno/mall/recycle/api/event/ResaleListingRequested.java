package com.suno.mall.recycle.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** REC-E005 v1：二次上架请求事实（回收单 LISTED，供商城域消费发布 listing）。 */
@UseCaseId("REC-E005")
@EventVersion(1)
public record ResaleListingRequested(
        String orderNo,
        String grade,
        java.math.BigDecimal estimatedPrice
) implements DocumentedDomainEvent {
}
