package com.dockflow.dockflow.document.messaging.rabbitmq;

/** Internal e-mail command carrying only the public event and configured recipient. */
public record DocumentEmailNotificationMessage(
    int schemaVersion,
    DocumentLifecycleEvent event,
    String recipient
) {
    public static final int SCHEMA_VERSION = 1;
}
