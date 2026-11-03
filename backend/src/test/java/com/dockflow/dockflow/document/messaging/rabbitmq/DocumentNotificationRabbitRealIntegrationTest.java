package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class DocumentNotificationRabbitRealIntegrationTest {

    private static final String SHORT_DELAY_QUEUE =
        "docflow.test.document-notifications.delay.100ms." + UUID.randomUUID();
    private static final String SHORT_DELAY_ROUTING_KEY =
        DocumentNotificationRabbitTopology.DELETION_WARNING_SCHEDULED_ROUTING_KEY;

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static CachingConnectionFactory connectionFactory;
    private static RabbitAdmin admin;
    private static RabbitTemplate rabbitTemplate;
    private static MessageConverter messageConverter;
    private static ObjectMapper objectMapper;

    @Mock
    private DocumentNotificationMessageConsumer consumer;

    @BeforeAll
    static void configureRabbit() {
        connectionFactory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        messageConverter = new Jackson2JsonMessageConverter();
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        admin = new RabbitAdmin(connectionFactory);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        declareTopology(new DocumentNotificationRabbitConfiguration());
    }

    @AfterAll
    static void closeRabbitConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void purgeQueues() {
        admin.purgeQueue(DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE, false);
        admin.purgeQueue(DocumentNotificationRabbitTopology.DELETION_WARNING_DELAY_QUEUE, false);
        admin.purgeQueue(DocumentNotificationRabbitTopology.EMAIL_REQUESTED_QUEUE, false);
        admin.purgeQueue(DocumentNotificationRabbitTopology.NOTIFICATIONS_DEAD_LETTER_QUEUE, false);
        admin.purgeQueue(DocumentNotificationRabbitTopology.EMAIL_DEAD_LETTER_QUEUE, false);
        DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.forEach(
            queue -> admin.purgeQueue(queue.name(), false)
        );
        admin.purgeQueue(SHORT_DELAY_QUEUE, false);
    }

    @Test
    void shouldRouteEveryLifecycleEventWithPersistentJsonAndPublisherConfirmation() throws Exception {
        DocumentLifecycleEvent failed = DocumentLifecycleEvent.failed(
            UUID.randomUUID(),
            Instant.now(),
            3,
            Instant.now().plus(Duration.ofHours(24))
        );
        List<DocumentLifecycleEvent> events = List.of(
            DocumentLifecycleEvent.processingStarted(UUID.randomUUID(), Instant.now()),
            DocumentLifecycleEvent.completed(UUID.randomUUID(), Instant.now()),
            failed,
            DocumentLifecycleEvent.deletionWarning(
                failed.documentId(),
                Instant.now(),
                failed.failureCount(),
                failed.exclusionScheduledAt()
            ),
            DocumentLifecycleEvent.deleted(UUID.randomUUID(), Instant.now())
        );
        RabbitMqDocumentLifecycleEventPublisher publisher =
            new RabbitMqDocumentLifecycleEventPublisher(
                rabbitTemplate,
                messageConverter,
                Duration.ofSeconds(3)
            );

        for (DocumentLifecycleEvent event : events) {
            publisher.publish(event);
        }

        try (var connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {
            Set<DocumentLifecycleEventType> receivedTypes = new java.util.HashSet<>();
            for (int index = 0; index < events.size(); index++) {
                GetResponse received = awaitDelivery(
                    channel,
                    DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
                    Duration.ofSeconds(3)
                );
                assertNotNull(received);
                assertEquals(2, received.getProps().getDeliveryMode());
                assertEquals(
                    DocumentNotificationRabbitTopology.JSON_CONTENT_TYPE,
                    received.getProps().getContentType()
                );
                String body = new String(received.getBody(), StandardCharsets.UTF_8);
                assertFalse(body.contains("storagePath"));
                DocumentLifecycleEvent parsed = objectMapper.readValue(body, DocumentLifecycleEvent.class);
                receivedTypes.add(parsed.type());
                channel.basicAck(received.getEnvelope().getDeliveryTag(), false);
            }
            assertEquals(Set.of(
                DocumentLifecycleEventType.DOCUMENT_PROCESSING_STARTED,
                DocumentLifecycleEventType.DOCUMENT_COMPLETED,
                DocumentLifecycleEventType.DOCUMENT_FAILED,
                DocumentLifecycleEventType.DOCUMENT_DELETION_WARNING,
                DocumentLifecycleEventType.DOCUMENT_DELETED
            ), receivedTypes);
        }
    }

    @Test
    void shouldAckThroughTheRealQueueAndRouteRejectedDeliveryToTheRealDeadLetterQueue() throws Exception {
        DocumentLifecycleEvent acknowledged = DocumentLifecycleEvent.completed(UUID.randomUUID(), Instant.now());
        when(consumer.process(acknowledged)).thenReturn(
            DocumentNotificationMessageConsumer.ProcessingResult.acknowledged(acknowledged.eventId())
        );
        publish(acknowledged, DocumentNotificationRabbitTopology.COMPLETED_ROUTING_KEY);

        try (var connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {
            GetResponse delivery = awaitDelivery(
                channel,
                DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
                Duration.ofSeconds(3)
            );
            assertNotNull(delivery);
            new RabbitMqDocumentNotificationMessageHandler(consumer).handle(
                acknowledged,
                deliveryMessage(delivery),
                channel
            );
            assertNull(channel.basicGet(
                DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
                true
            ));

            DocumentLifecycleEvent rejected = DocumentLifecycleEvent.failed(
                UUID.randomUUID(),
                Instant.now(),
                1,
                Instant.now().plus(Duration.ofHours(24))
            );
            when(consumer.process(rejected)).thenReturn(
                DocumentNotificationMessageConsumer.ProcessingResult.deadLetter(rejected.eventId())
            );
            publish(rejected, DocumentNotificationRabbitTopology.FAILED_ROUTING_KEY);
            GetResponse rejectedDelivery = awaitDelivery(
                channel,
                DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
                Duration.ofSeconds(3)
            );
            assertNotNull(rejectedDelivery);
            new RabbitMqDocumentNotificationMessageHandler(consumer).handle(
                rejected,
                deliveryMessage(rejectedDelivery),
                channel
            );
        }

        assertNotNull(rabbitTemplate.receive(
            DocumentNotificationRabbitTopology.NOTIFICATIONS_DEAD_LETTER_QUEUE,
            5_000
        ));
    }

    @Test
    void shouldExposeRabbitRedeliveryWhenAConsumerNacksWithRequeue() throws Exception {
        DocumentLifecycleEvent event = DocumentLifecycleEvent.processingStarted(UUID.randomUUID(), Instant.now());
        publish(event, DocumentNotificationRabbitTopology.PROCESSING_STARTED_ROUTING_KEY);

        try (var connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {
            GetResponse firstDelivery = awaitDelivery(
                channel,
                DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
                Duration.ofSeconds(3)
            );
            assertNotNull(firstDelivery);
            assertFalse(firstDelivery.getEnvelope().isRedeliver());
            channel.basicNack(firstDelivery.getEnvelope().getDeliveryTag(), false, true);

            GetResponse redelivery = awaitDelivery(
                channel,
                DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
                Duration.ofSeconds(3)
            );
            assertNotNull(redelivery);
            assertTrue(redelivery.getEnvelope().isRedeliver());
            channel.basicAck(redelivery.getEnvelope().getDeliveryTag(), false);
        }
    }

    @Test
    void shouldApplyProductionDelayContractAndRouteExpiredShortDelayThroughTheRealDlx() throws Exception {
        DocumentNotificationRabbitConfiguration configuration = new DocumentNotificationRabbitConfiguration();
        Queue productionDelay = configuration.documentNotificationDeletionWarningDelayQueue();
        assertEquals(
            DocumentNotificationRabbitTopology.DELETION_WARNING_DELAY.toMillis(),
            productionDelay.getArguments().get("x-message-ttl")
        );

        DocumentLifecycleEvent warning = DocumentLifecycleEvent.deletionWarning(
            UUID.randomUUID(),
            Instant.now(),
            3,
            Instant.now().plus(Duration.ofHours(24))
        );
        publish(warning, SHORT_DELAY_ROUTING_KEY);

        try (var connection = connectionFactory.createConnection();
             Channel channel = connection.createChannel(false)) {
            GetResponse received = awaitDelivery(
                channel,
                DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
                Duration.ofSeconds(5)
            );
            assertNotNull(received);
            assertEquals(
                DocumentNotificationRabbitTopology.DELETION_WARNING_ROUTING_KEY,
                received.getEnvelope().getRoutingKey()
            );
            channel.basicAck(received.getEnvelope().getDeliveryTag(), false);
        }
    }

    private static void declareTopology(DocumentNotificationRabbitConfiguration configuration) {
        TopicExchange eventsExchange = configuration.documentNotificationEventsExchange();
        TopicExchange notificationDlx = configuration.documentNotificationDeadLetterExchange();
        TopicExchange emailDlx = configuration.documentEmailNotificationDeadLetterExchange();
        Queue notifications = configuration.documentNotificationRequestedQueue();
        Queue delay = configuration.documentNotificationDeletionWarningDelayQueue();
        Queue email = configuration.documentEmailNotificationRequestedQueue();
        Queue notificationDeadLetter = configuration.documentNotificationDeadLetterQueue();
        Queue emailDeadLetter = configuration.documentEmailNotificationDeadLetterQueue();

        admin.declareExchange(eventsExchange);
        admin.declareExchange(notificationDlx);
        admin.declareExchange(emailDlx);
        List<Queue> queues = List.of(
            notifications,
            delay,
            email,
            notificationDeadLetter,
            emailDeadLetter,
            configuration.documentEmailNotificationRetry30sQueue(),
            configuration.documentEmailNotificationRetry5mQueue(),
            configuration.documentEmailNotificationRetry30mQueue(),
            shortDelayQueue()
        );
        queues.forEach(admin::declareQueue);

        List<Binding> bindings = List.of(
            configuration.documentNotificationProcessingStartedBinding(notifications, eventsExchange),
            configuration.documentNotificationCompletedBinding(notifications, eventsExchange),
            configuration.documentNotificationFailedBinding(notifications, eventsExchange),
            configuration.documentNotificationDeletionWarningBinding(notifications, eventsExchange),
            configuration.documentNotificationDeletedBinding(notifications, eventsExchange),
            configuration.documentNotificationDeletionWarningDelayBinding(delay, eventsExchange),
            configuration.documentEmailNotificationRequestedBinding(email, eventsExchange),
            configuration.documentNotificationDeadLetterBinding(notificationDeadLetter, notificationDlx),
            configuration.documentEmailNotificationDeadLetterBinding(emailDeadLetter, emailDlx),
            new Binding(
                SHORT_DELAY_QUEUE,
                Binding.DestinationType.QUEUE,
                DocumentNotificationRabbitTopology.EVENTS_EXCHANGE,
                SHORT_DELAY_ROUTING_KEY,
                Map.of()
            )
        );
        bindings.forEach(admin::declareBinding);
    }

    private static Queue shortDelayQueue() {
        return new Queue(
            SHORT_DELAY_QUEUE,
            true,
            false,
            false,
            Map.of(
                "x-message-ttl", 100,
                "x-dead-letter-exchange", DocumentNotificationRabbitTopology.EVENTS_EXCHANGE,
                "x-dead-letter-routing-key", DocumentNotificationRabbitTopology.DELETION_WARNING_ROUTING_KEY
            )
        );
    }

    private void publish(DocumentLifecycleEvent event, String routingKey) {
        Message outbound = messageConverter.toMessage(event, new MessageProperties());
        MessageProperties properties = outbound.getMessageProperties();
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setContentType(DocumentNotificationRabbitTopology.JSON_CONTENT_TYPE);
        rabbitTemplate.send(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE, routingKey, outbound);
    }

    private static GetResponse awaitDelivery(Channel channel, String queue, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        GetResponse received;
        do {
            received = channel.basicGet(queue, false);
            if (received != null) {
                return received;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        return null;
    }

    private static Message deliveryMessage(GetResponse received) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(received.getEnvelope().getDeliveryTag());
        return new Message(received.getBody(), properties);
    }
}
