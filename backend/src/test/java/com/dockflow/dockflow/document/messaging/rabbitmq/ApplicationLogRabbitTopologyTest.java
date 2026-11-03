package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationLogRabbitTopologyTest {

    @Test
    void shouldExposeApprovedDurableTopologyAndRoutingKeys() {
        assertEquals("docflow.application-logs", ApplicationLogRabbitTopology.EXCHANGE);
        assertEquals("docflow.application-logs.persist", ApplicationLogRabbitTopology.MAIN_QUEUE);
        assertEquals("docflow.application-logs.dlx", ApplicationLogRabbitTopology.DEAD_LETTER_EXCHANGE);
        assertEquals("docflow.application-logs.dlq", ApplicationLogRabbitTopology.DEAD_LETTER_QUEUE);
        assertEquals("application.log.info", RabbitMqApplicationLogPublisher.routingKey(ApplicationLogLevel.INFO));
        assertEquals("application.log.warn", RabbitMqApplicationLogPublisher.routingKey(ApplicationLogLevel.WARN));
        assertEquals("application.log.error", RabbitMqApplicationLogPublisher.routingKey(ApplicationLogLevel.ERROR));
        assertEquals(
            java.util.List.of(Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(60)),
            ApplicationLogRabbitTopology.RETRY_QUEUES.stream().map(ApplicationLogRabbitTopology.RetryQueue::delay).toList()
        );
    }
}
