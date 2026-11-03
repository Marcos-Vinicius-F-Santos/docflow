package com.dockflow.dockflow.document.messaging.rabbitmq;

import java.util.ArrayList;
import java.util.List;

/** Test-only publisher double that captures events instead of using RabbitMQ. */
public final class InMemoryDocumentLifecycleEventPublisher {

    private final List<DocumentLifecycleEvent> publishedEvents = new ArrayList<>();

    public void publish(DocumentLifecycleEvent event) {
        publishedEvents.add(event);
    }

    public List<DocumentLifecycleEvent> publishedEvents() {
        return List.copyOf(publishedEvents);
    }
}
