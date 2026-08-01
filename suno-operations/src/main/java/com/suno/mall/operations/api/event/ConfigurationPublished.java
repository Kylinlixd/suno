package com.suno.mall.operations.api.event;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;

/** Public fact emitted after an operations configuration publication. */
@UseCaseId("OPS-E004")
@EventVersion(1)
public record ConfigurationPublished(String catalogVersion) implements DocumentedDomainEvent {
}
