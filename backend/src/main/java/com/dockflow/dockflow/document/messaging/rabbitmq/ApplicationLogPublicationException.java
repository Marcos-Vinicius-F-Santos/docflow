package com.dockflow.dockflow.document.messaging.rabbitmq;

/** Signals that a log message could not be confirmed by RabbitMQ. */
public class ApplicationLogPublicationException extends RuntimeException {

    public ApplicationLogPublicationException(String message) {
        super(message);
    }

    public ApplicationLogPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
