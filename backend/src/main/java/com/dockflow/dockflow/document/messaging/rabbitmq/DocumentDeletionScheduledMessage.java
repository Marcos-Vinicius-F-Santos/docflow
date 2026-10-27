package com.dockflow.dockflow.document.messaging.rabbitmq;

import java.util.UUID;

/** Versioned RabbitMQ payload for delayed document deletion. */
public record DocumentDeletionScheduledMessage(int schemaVersion, UUID documentId) {
}
