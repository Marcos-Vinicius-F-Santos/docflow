package com.dockflow.dockflow.document.messaging.rabbitmq;

/** Signals a notification message that cannot be safely processed or retried. */
public class DocumentNotificationMessageValidationException extends RuntimeException {

    public DocumentNotificationMessageValidationException(String message) {
        super(message);
    }
}
