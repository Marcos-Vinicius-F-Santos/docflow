package com.dockflow.dockflow.document.messaging.rabbitmq;

/** Indicates that a deletion message cannot be safely interpreted. */
public class DocumentDeletionMessageValidationException extends RuntimeException {

    public DocumentDeletionMessageValidationException(String message) {
        super(message);
    }
}
