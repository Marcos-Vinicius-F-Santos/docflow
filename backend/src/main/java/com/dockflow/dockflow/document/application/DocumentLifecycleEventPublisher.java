package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;

/** Application boundary for publishing a document lifecycle notification event. */
public interface DocumentLifecycleEventPublisher {

    void publish(DocumentLifecycleEvent event);

    /** Publishes a warning to the approved delay route before final delivery. */
    default void scheduleDeletionWarning(DocumentLifecycleEvent event) {
        publish(event);
    }
}
