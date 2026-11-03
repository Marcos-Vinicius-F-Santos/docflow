package com.dockflow.dockflow.document.messaging.rabbitmq;

public class ApplicationLogMessageValidationException extends RuntimeException {

    public ApplicationLogMessageValidationException(String message) {
        super(message);
    }
}
