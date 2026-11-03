package com.dockflow.dockflow.document.adapter.out.email;

/** Classified failure from the e-mail provider boundary. */
public class EmailDeliveryException extends RuntimeException {

    private final FailureType failureType;

    public EmailDeliveryException(FailureType failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public EmailDeliveryException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType failureType() {
        return failureType;
    }

    public enum FailureType {
        TRANSIENT,
        PERMANENT
    }
}
