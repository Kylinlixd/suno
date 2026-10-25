package com.suno.mall.recycle.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** REC-E004 v1：回收入账事实（积分流水写入，幂等由回收单唯一性保证）。 */
@UseCaseId("REC-E004")
@EventVersion(1)
public record RecyclePointsPosted(
        String orderNo,
        Long userId,
        int points
) implements DocumentedDomainEvent {
}
