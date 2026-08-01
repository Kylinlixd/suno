package com.suno.mall.core.event;

/**
 * Public fact emitted when a versioned catalog of cross-module events is published.
 */
@UseCaseId("core.event-catalog.published")
public record EventCatalogPublished(String catalogVersion) implements DocumentedDomainEvent {
}
