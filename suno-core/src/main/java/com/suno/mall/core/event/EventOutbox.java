package com.suno.mall.core.event;

/**
 * Port for durably handing a public domain event to asynchronous delivery.
 */
public interface EventOutbox {

    void publish(DomainEvent event);
}
