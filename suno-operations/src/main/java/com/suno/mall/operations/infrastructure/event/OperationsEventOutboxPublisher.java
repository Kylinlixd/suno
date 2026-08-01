package com.suno.mall.operations.infrastructure.event;

import com.suno.mall.core.event.DomainEvent;
import com.suno.mall.core.event.EventOutbox;
import com.suno.mall.operations.api.event.OperationsEventPublisher;

/** Infrastructure adapter that hands public-event delivery to the shared outbox. */
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
