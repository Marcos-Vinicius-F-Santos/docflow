package com.dockflow.dockflow.document.application.logging;

public class ApplicationLogQueryUnavailableException extends RuntimeException {

    public ApplicationLogQueryUnavailableException(Throwable cause) {
        super("Operational logs are temporarily unavailable", cause);
    }
}
