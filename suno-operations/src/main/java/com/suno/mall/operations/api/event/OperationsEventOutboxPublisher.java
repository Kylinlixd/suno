package com.suno.mall.operations.api.event;

import com.suno.mall.core.event.DomainEvent;
import com.suno.mall.core.event.EventOutbox;

/**
 * Operations adapter that hands every public-event delivery verb to the shared outbox.
 */
public final class OperationsEventOutboxPublisher implements OperationsEventPublisher {

    private final EventOutbox eventOutbox;

    public OperationsEventOutboxPublisher(EventOutbox eventOutbox) {
        this.eventOutbox = eventOutbox;
    }

    @Override
    public void publish(DomainEvent event) {
        eventOutbox.publish(event);
    }

    @Override
    public void emit(DomainEvent event) {
        eventOutbox.publish(event);
    }

    @Override
    public void send(DomainEvent event) {
        eventOutbox.publish(event);
    }
}
