package com.dockflow.dockflow.document.messaging.rabbitmq;

/** Public lifecycle event types exposed to notification consumers. */
public enum DocumentLifecycleEventType {
    DOCUMENT_PROCESSING_STARTED,
    DOCUMENT_COMPLETED,
    DOCUMENT_FAILED,
    DOCUMENT_DELETION_WARNING,
    DOCUMENT_DELETED
}
