package com.suno.mall.operations.api.event;

import com.suno.mall.core.event.DomainEvent;

/**
 * Operations public-event port. All delivery verbs accept only a {@link DomainEvent}.
 */
public interface OperationsEventPublisher {

    void publish(DomainEvent event);

    void emit(DomainEvent event);

    void send(DomainEvent event);
}
