package com.suno.mall.operations.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.UseCaseId;

/**
 * Public fact emitted after operations escalates a cross-domain case.
 */
@UseCaseId("operations.case.escalated")
public record OperationsCaseEscalated(String caseId) implements DocumentedDomainEvent {
}
