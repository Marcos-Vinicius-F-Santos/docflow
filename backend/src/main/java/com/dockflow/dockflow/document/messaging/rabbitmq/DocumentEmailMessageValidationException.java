package com.dockflow.dockflow.document.messaging.rabbitmq;

/** Signals an e-mail command that must go directly to the e-mail DLQ. */
public class DocumentEmailMessageValidationException extends RuntimeException {

    public DocumentEmailMessageValidationException(String message) {
        super(message);
    }
}
