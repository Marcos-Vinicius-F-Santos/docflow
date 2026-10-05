package com.dockflow.dockflow.document.application;

import java.util.UUID;

/**
 * Application boundary for publishing a storage request.
 *
 * <p>The contract contains only durable identifiers and metadata. Content streams
 * and Spring Web types must never cross this boundary.</p>
 */
public interface DocumentStorageRequestPublisher {

    void publish(DocumentStorageRequested request);

    record DocumentStorageRequested(
        int schemaVersion,
        UUID documentId,
        String objectKey,
        String contentReference,
        long sizeBytes,
        String contentType
    ) {
    }
}
