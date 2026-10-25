package com.suno.mall.marketplace.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** MKT-E006 v1：评价举报事实（供审核风控订阅）。 */
@UseCaseId("MKT-E006")
@EventVersion(1)
public record MarketplaceReviewReported(
        String orderNo,
        Long reporterUserId,
        String reason
) implements DocumentedDomainEvent {
}
