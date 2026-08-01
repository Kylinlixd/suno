package com.suno.mall.operations.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** Public fact emitted after an operations security incident is recorded. */
@UseCaseId("OPS-E002")
@EventVersion(1)
public record SecurityIncidentRecorded(String caseId) implements DocumentedDomainEvent {
}
