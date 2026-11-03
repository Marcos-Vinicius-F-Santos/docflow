package com.dockflow.dockflow.document.messaging.rabbitmq;

import java.time.Duration;
import java.util.List;

/** Physical RabbitMQ topology for document notifications, as approved by ADR-008. */
public final class DocumentNotificationRabbitTopology {

    public static final String EVENTS_EXCHANGE = "docflow.document-events";
    public static final String NOTIFICATIONS_REQUESTED_QUEUE =
        "docflow.document-notifications.requested";
    public static final String DELETION_WARNING_DELAY_QUEUE =
        "docflow.document-notifications.delay.2h30";
    public static final String EMAIL_REQUESTED_QUEUE =
        "docflow.document-email-notifications.requested";
    public static final String EMAIL_ROUTING_KEY = "notification.email.requested";

    public static final String PROCESSING_STARTED_ROUTING_KEY = "document.processing.started";
    public static final String COMPLETED_ROUTING_KEY = "document.completed";
    public static final String FAILED_ROUTING_KEY = "document.failed";
    public static final String DELETION_WARNING_SCHEDULED_ROUTING_KEY =
        "document.deletion.warning.scheduled";
    public static final String DELETION_WARNING_ROUTING_KEY = "document.deletion.warning";
    public static final String DELETED_ROUTING_KEY = "document.deleted";

    public static final String NOTIFICATIONS_DEAD_LETTER_EXCHANGE =
        "docflow.document-notifications.dlx";
    public static final String NOTIFICATIONS_DEAD_LETTER_QUEUE =
        "docflow.document-notifications.dlq";
    public static final String EMAIL_DEAD_LETTER_EXCHANGE =
        "docflow.document-email-notifications.dlx";
    public static final String EMAIL_DEAD_LETTER_QUEUE =
        "docflow.document-email-notifications.dlq";

    public static final Duration DELETION_WARNING_DELAY = Duration.ofHours(2).plusMinutes(30);
    public static final List<RetryQueue> EMAIL_RETRY_QUEUES = List.of(
        new RetryQueue("docflow.document-email-notifications.retry.30s", Duration.ofSeconds(30)),
        new RetryQueue("docflow.document-email-notifications.retry.5m", Duration.ofMinutes(5)),
        new RetryQueue("docflow.document-email-notifications.retry.30m", Duration.ofMinutes(30))
    );

    public static final int PERSISTENT_DELIVERY_MODE = 2;
    public static final String JSON_CONTENT_TYPE = "application/json";

    private DocumentNotificationRabbitTopology() {
    }

    public record RetryQueue(String name, Duration delay) {
    }
}
