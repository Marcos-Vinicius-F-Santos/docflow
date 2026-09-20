package com.dockflow.dockflow.document.messaging.rabbitmq;

import java.util.UUID;

/**
 * Versioned wire contract for a document storage request.
 *
 * <p>This message carries only durable metadata and a staging reference. It
 * must never receive an InputStream or the binary content itself.</p>
 */
public record DocumentStorageRequestedMessage(
    int schemaVersion,
    UUID documentId,
    String objectKey,
    String contentReference,
    long sizeBytes,
    String contentType
) {
}
