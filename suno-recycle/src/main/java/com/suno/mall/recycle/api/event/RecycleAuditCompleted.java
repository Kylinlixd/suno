package com.suno.mall.recycle.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** REC-E002 v1：回收审核完成事实（每步状态迁移均发布）。 */
@UseCaseId("REC-E002")
@EventVersion(1)
public record RecycleAuditCompleted(
        String orderNo,
        String action,
        String fromStatus,
        String toStatus
) implements DocumentedDomainEvent {
}
