package com.suno.mall.identity.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** IDN-E001 v1：认证成功事实（登录/注册即登录/短信登录），供风险与运营订阅。 */
@UseCaseId("IDN-E001")
@EventVersion(1)
public record UserAuthenticated(
        String username,
        String deviceId,
        String via
) implements DocumentedDomainEvent {
}
