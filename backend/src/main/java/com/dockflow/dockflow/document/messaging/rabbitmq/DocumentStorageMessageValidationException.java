package com.dockflow.dockflow.document.messaging.rabbitmq;

/** A message that cannot be safely processed or retried. */
public class DocumentStorageMessageValidationException extends RuntimeException {

    public DocumentStorageMessageValidationException(String message) {
        super(message);
    }
}
