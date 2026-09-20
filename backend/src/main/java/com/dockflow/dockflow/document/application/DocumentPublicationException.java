package com.dockflow.dockflow.document.application;

import java.util.UUID;

/** Indicates that RabbitMQ did not clearly confirm a document publication. */
public class DocumentPublicationException extends RuntimeException {

    private final UUID documentId;

    public DocumentPublicationException(UUID documentId, String message, Throwable cause) {
        super(message, cause);
        this.documentId = documentId;
    }

    public DocumentPublicationException(UUID documentId, String message) {
        this(documentId, message, null);
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
