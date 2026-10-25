package com.suno.mall.recycle.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** REC-E001 v1：回收单提交事实（服务端估价与积分随后发布）。 */
@UseCaseId("REC-E001")
@EventVersion(1)
public record RecycleOrderSubmitted(
        String orderNo,
        Long userId,
        int wearScore
) implements DocumentedDomainEvent {
}
