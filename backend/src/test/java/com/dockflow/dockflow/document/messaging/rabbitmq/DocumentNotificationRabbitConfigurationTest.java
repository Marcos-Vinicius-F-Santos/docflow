package com.dockflow.dockflow.document.messaging.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentNotificationRabbitConfigurationTest {

    private final DocumentNotificationRabbitConfiguration configuration =
        new DocumentNotificationRabbitConfiguration();

    @Test
    void shouldDeclareTheDurableTopicExchanges() {
        TopicExchange events = configuration.documentNotificationEventsExchange();
        TopicExchange notificationDlx = configuration.documentNotificationDeadLetterExchange();
        TopicExchange emailDlx = configuration.documentEmailNotificationDeadLetterExchange();

        assertEquals(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE, events.getName());
        assertEquals(DocumentNotificationRabbitTopology.NOTIFICATIONS_DEAD_LETTER_EXCHANGE,
            notificationDlx.getName());
        assertEquals(DocumentNotificationRabbitTopology.EMAIL_DEAD_LETTER_EXCHANGE, emailDlx.getName());
        assertTrue(events.isDurable());
        assertTrue(notificationDlx.isDurable());
        assertTrue(emailDlx.isDurable());
    }

    @Test
    void shouldDeclareNotificationAndEmailQueuesWithApprovedBindings() {
        Queue notifications = configuration.documentNotificationRequestedQueue();
        Queue email = configuration.documentEmailNotificationRequestedQueue();

        assertEquals(DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE, notifications.getName());
        assertEquals(DocumentNotificationRabbitTopology.EMAIL_REQUESTED_QUEUE, email.getName());
        assertTrue(notifications.isDurable());
        assertTrue(email.isDurable());

        TopicExchange events = configuration.documentNotificationEventsExchange();
        Set<String> notificationKeys = Set.of(
            configuration.documentNotificationProcessingStartedBinding(notifications, events).getRoutingKey(),
            configuration.documentNotificationCompletedBinding(notifications, events).getRoutingKey(),
            configuration.documentNotificationFailedBinding(notifications, events).getRoutingKey(),
            configuration.documentNotificationDeletionWarningBinding(notifications, events).getRoutingKey(),
            configuration.documentNotificationDeletedBinding(notifications, events).getRoutingKey()
        );
        assertEquals(Set.of(
            "document.processing.started",
            "document.completed",
            "document.failed",
            "document.deletion.warning",
            "document.deleted"
        ), notificationKeys);
        assertEquals(
            DocumentNotificationRabbitTopology.EMAIL_ROUTING_KEY,
            configuration.documentEmailNotificationRequestedBinding(email, events).getRoutingKey()
        );
    }

    @Test
    void shouldDeclareTheTwoHourThirtyMinuteWarningDelay() {
        Queue delay = configuration.documentNotificationDeletionWarningDelayQueue();
        Binding binding = configuration.documentNotificationDeletionWarningDelayBinding(
            delay,
            configuration.documentNotificationEventsExchange()
        );

        assertEquals(DocumentNotificationRabbitTopology.DELETION_WARNING_DELAY_QUEUE, delay.getName());
        assertTrue(delay.isDurable());
        assertEquals(DocumentNotificationRabbitTopology.DELETION_WARNING_DELAY.toMillis(),
            delay.getArguments().get("x-message-ttl"));
        assertEquals(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE,
            delay.getArguments().get("x-dead-letter-exchange"));
        assertEquals(DocumentNotificationRabbitTopology.DELETION_WARNING_ROUTING_KEY,
            delay.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(DocumentNotificationRabbitTopology.DELETION_WARNING_SCHEDULED_ROUTING_KEY,
            binding.getRoutingKey());
    }

    @Test
    void shouldDeclareEmailRetriesWithThirtySecondFiveMinuteAndThirtyMinuteDelays() {
        List<Queue> queues = List.of(
            configuration.documentEmailNotificationRetry30sQueue(),
            configuration.documentEmailNotificationRetry5mQueue(),
            configuration.documentEmailNotificationRetry30mQueue()
        );

        assertEquals(
            DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.stream()
                .map(DocumentNotificationRabbitTopology.RetryQueue::name)
                .collect(Collectors.toList()),
            queues.stream().map(Queue::getName).collect(Collectors.toList())
        );
        for (int index = 0; index < queues.size(); index++) {
            Queue queue = queues.get(index);
            DocumentNotificationRabbitTopology.RetryQueue retry =
                DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.get(index);
            assertTrue(queue.isDurable());
            assertEquals(retry.delay().toMillis(), queue.getArguments().get("x-message-ttl"));
            assertEquals(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange"));
            assertEquals(DocumentNotificationRabbitTopology.EMAIL_ROUTING_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
        }
    }

    @Test
    void shouldMakeNotificationMessagesPersistentJsonAndUsePublisherConfirms() throws Exception {
        Message message = new Message(new byte[] { '{', '}' }, new MessageProperties());
        Message processed = configuration.documentNotificationPersistentMessagePostProcessor()
            .postProcessMessage(message);

        assertEquals(MessageDeliveryMode.PERSISTENT, processed.getMessageProperties().getDeliveryMode());
        assertEquals("application/json", processed.getMessageProperties().getContentType());

        String properties = new String(
            getClass().getClassLoader().getResourceAsStream("application.properties").readAllBytes()
        );
        assertTrue(properties.contains("spring.rabbitmq.publisher-confirm-type=correlated"));
    }
}
