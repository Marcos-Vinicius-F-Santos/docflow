package com.dockflow.dockflow.document.messaging.rabbitmq;

import java.time.Duration;
import java.util.List;

/** Physical RabbitMQ contract for operational log delivery. */
public final class ApplicationLogRabbitTopology {

    public static final String EXCHANGE = "docflow.application-logs";
    public static final String MAIN_QUEUE = "docflow.application-logs.persist";
    public static final String INFO_ROUTING_KEY = "application.log.info";
    public static final String WARN_ROUTING_KEY = "application.log.warn";
    public static final String ERROR_ROUTING_KEY = "application.log.error";
    public static final String RETRY_ROUTING_KEY = INFO_ROUTING_KEY;
    public static final String DEAD_LETTER_EXCHANGE = "docflow.application-logs.dlx";
    public static final String DEAD_LETTER_QUEUE = "docflow.application-logs.dlq";
    public static final int MESSAGE_SCHEMA_VERSION = 1;
    public static final Duration PUBLISH_CONFIRMATION_TIMEOUT = Duration.ofSeconds(3);

    public static final List<RetryQueue> RETRY_QUEUES = List.of(
        new RetryQueue("docflow.application-logs.retry.5s", Duration.ofSeconds(5)),
        new RetryQueue("docflow.application-logs.retry.15s", Duration.ofSeconds(15)),
        new RetryQueue("docflow.application-logs.retry.60s", Duration.ofSeconds(60))
    );

    private ApplicationLogRabbitTopology() {
    }

    public record RetryQueue(String name, Duration delay) {
    }
}
