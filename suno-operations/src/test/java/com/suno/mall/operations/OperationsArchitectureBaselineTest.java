package com.suno.mall.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.DomainEvent;
import com.suno.mall.core.event.EventOutbox;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationsArchitectureBaselineTest {

    @Test
    void operationsLayersExposeConcreteDomainApplicationAndEventContracts() throws Exception {
        Class<?> priorityType = Class.forName("com.suno.mall.operations.domain.OperationsCasePriority");
        Class<?> useCaseType = Class.forName("com.suno.mall.operations.application.OperationsCaseEscalationUseCase");
        Class<?> eventType = Class.forName("com.suno.mall.operations.api.event.SecurityIncidentRecorded");
        Class<?> publisherType = Class.forName("com.suno.mall.operations.api.event.OperationsEventPublisher");
        Class<?> adapterType = Class.forName("com.suno.mall.operations.infrastructure.event.OperationsEventOutboxPublisher");

        Method requiresImmediateHandling = priorityType.getMethod("requiresImmediateHandling");
        Method valueOf = priorityType.getMethod("valueOf", String.class);
        Object routine = valueOf.invoke(null, "ROUTINE");
        Object urgent = valueOf.invoke(null, "URGENT");
        assertFalse((boolean) requiresImmediateHandling.invoke(routine));
        assertTrue((boolean) requiresImmediateHandling.invoke(urgent));
        assertTrue(useCaseType.isInterface());
        assertTrue(DocumentedDomainEvent.class.isAssignableFrom(eventType));
        assertTrue(DomainEvent.class.isAssignableFrom(eventType));
        assertTrue(Arrays.stream(publisherType.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet())
                .containsAll(Set.of("publish", "emit", "send")));

        AtomicInteger published = new AtomicInteger();
        EventOutbox outbox = event -> published.incrementAndGet();
        Object event = eventType.getConstructor(String.class).newInstance("case-42");
        Object adapter = adapterType.getConstructor(EventOutbox.class).newInstance(outbox);
        for (String methodName : List.of("publish", "emit", "send")) {
            adapterType.getMethod(methodName, DomainEvent.class).invoke(adapter, event);
        }
        assertEquals(3, published.get());
    }
}
