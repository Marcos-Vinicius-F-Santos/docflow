package com.dockflow.dockflow.document.messaging.rabbitmq;

import java.time.Duration;
import java.util.List;

/**
 * Provider-neutral names and timing contract for document storage messaging.
 *
 * <p>This class defines the feature contract used by the Spring AMQP bindings,
 * publisher confirms and consumer adapter.</p>
 */
public final class DocumentStorageRabbitTopology {

    public static final int MESSAGE_SCHEMA_VERSION = 1;
    public static final int PERSISTENT_DELIVERY_MODE = 2;
    public static final String EXCHANGE = "docflow.document-storage";
    public static final String ROUTING_KEY = "document.storage.requested";
    public static final String MAIN_QUEUE = "docflow.document-storage.requested";
    public static final String DEAD_LETTER_EXCHANGE = "docflow.document-storage.dlx";
    public static final String DEAD_LETTER_QUEUE = "docflow.document-storage.dlq";
    public static final int DELETION_MESSAGE_SCHEMA_VERSION = 1;
    public static final String DELETION_EXCHANGE = "docflow.document-deletion";
    public static final String DELETION_DELAY_QUEUE = "docflow.document-deletion.delay.3h";
    public static final String DELETION_REQUESTED_QUEUE = "docflow.document-deletion.requested";
    public static final String DELETION_SCHEDULED_ROUTING_KEY = "document.deletion.scheduled";
    public static final String DELETION_REQUESTED_ROUTING_KEY = "document.deletion.requested";
    public static final String DELETION_DEAD_LETTER_EXCHANGE = "docflow.document-deletion.dlx";
    public static final String DELETION_DEAD_LETTER_QUEUE = "docflow.document-deletion.dlq";
    public static final Duration DELETION_DELAY = Duration.ofHours(3);
    public static final int PREFETCH_COUNT = 1;
    public static final int MIN_CONCURRENCY = 1;
    public static final int MAX_CONCURRENCY = 1;
    public static final String LISTENER_CONCURRENCY = "1";
    public static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(3);

    public static final List<RetryQueue> RETRY_QUEUES = List.of(
        new RetryQueue("docflow.document-storage.retry.5s", Duration.ofSeconds(5)),
        new RetryQueue("docflow.document-storage.retry.15s", Duration.ofSeconds(15)),
        new RetryQueue("docflow.document-storage.retry.60s", Duration.ofSeconds(60))
    );

    private DocumentStorageRabbitTopology() {
    }

    public record RetryQueue(String name, Duration delay) {
    }
}
