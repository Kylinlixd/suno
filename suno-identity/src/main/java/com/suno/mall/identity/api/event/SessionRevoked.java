package com.suno.mall.identity.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** IDN-E003 v1：会话撤销事实（本人/管理员操作或重放检测触发）。 */
@UseCaseId("IDN-E003")
@EventVersion(1)
public record SessionRevoked(
        String username,
        String deviceId,
        String reason
) implements DocumentedDomainEvent {
}
