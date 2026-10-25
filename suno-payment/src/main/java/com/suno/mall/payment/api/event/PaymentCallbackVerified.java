package com.suno.mall.payment.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** PAY-E001 v1：回调验签通过事实（无论后续业务成功与否）。 */
@UseCaseId("PAY-E001")
@EventVersion(1)
public record PaymentCallbackVerified(
        String orderNo,
        String idempotencyKey,
        String callbackStatus
) implements DocumentedDomainEvent {
}
