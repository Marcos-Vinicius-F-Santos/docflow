package com.dockflow.dockflow.document.application;

import java.util.UUID;

/** Application boundary for scheduling the delayed deletion of a document. */
public interface DocumentDeletionPublisher {

    void publish(DocumentDeletionScheduled request);

    record DocumentDeletionScheduled(int schemaVersion, UUID documentId) {
    }
}
