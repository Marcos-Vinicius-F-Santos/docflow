package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentDeletionPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentDeletionRealIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static CachingConnectionFactory connectionFactory;
    private static RabbitAdmin admin;
    private static RabbitTemplate rabbitTemplate;
    private static ObjectMapper objectMapper;

    @Mock
    private DocumentDeletionMessageConsumer consumer;

    @BeforeAll
    static void configureRabbit() {
        connectionFactory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        MessageConverter messageConverter = new Jackson2JsonMessageConverter();
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        admin = new RabbitAdmin(connectionFactory);
        objectMapper = new ObjectMapper();

        DocumentStorageRabbitConfiguration configuration = new DocumentStorageRabbitConfiguration();
        admin.declareExchange(configuration.documentDeletionExchange());
        admin.declareExchange(configuration.documentDeletionDeadLetterExchange());
        admin.declareQueue(configuration.documentDeletionDelayQueue());
        admin.declareQueue(configuration.documentDeletionRequestedQueue());
        admin.declareQueue(configuration.documentDeletionDeadLetterQueue());
        admin.declareBinding(configuration.documentDeletionDelayQueueBinding(
            configuration.documentDeletionDelayQueue(),
            configuration.documentDeletionExchange()
        ));
        admin.declareBinding(configuration.documentDeletionRequestedQueueBinding(
            configuration.documentDeletionRequestedQueue(),
            configuration.documentDeletionExchange()
        ));
        admin.declareBinding(configuration.documentDeletionDeadLetterQueueBinding(
            configuration.documentDeletionDeadLetterQueue(),
            configuration.documentDeletionDeadLetterExchange()
        ));
    }

    @AfterAll
    static void closeRabbitConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void purgeQueues() {
        admin.purgeQueue(DocumentStorageRabbitTopology.DELETION_DELAY_QUEUE, false);
        admin.purgeQueue(DocumentStorageRabbitTopology.DELETION_REQUESTED_QUEUE, false);
        admin.purgeQueue(DocumentStorageRabbitTopology.DELETION_DEAD_LETTER_QUEUE, false);
    }

    @Test
    void shouldPublishPersistentMinimalDeletionMessageToTheRealDelayQueue() throws Exception {
        UUID documentId = UUID.randomUUID();

        new RabbitMqDocumentDeletionPublisher(
            rabbitTemplate,
            new Jackson2JsonMessageConverter(),
            Duration.ofSeconds(3)
        ).publish(new DocumentDeletionPublisher.DocumentDeletionScheduled(
            DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
            documentId
        ));

        var connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        GetResponse received = awaitBasicGet(channel, DocumentStorageRabbitTopology.DELETION_DELAY_QUEUE);

        assertNotNull(received);
        assertEquals(2, received.getProps().getDeliveryMode());
        assertEquals("application/json", received.getProps().getContentType());
        DocumentDeletionScheduledMessage parsed = objectMapper.readValue(
            received.getBody(),
            DocumentDeletionScheduledMessage.class
        );
        assertEquals(DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION, parsed.schemaVersion());
        assertEquals(documentId, parsed.documentId());
        assertEquals("{\"schemaVersion\":1,\"documentId\":\"" + documentId + "\"}",
            new String(received.getBody(), java.nio.charset.StandardCharsets.UTF_8));
        channel.basicAck(received.getEnvelope().getDeliveryTag(), false);
        channel.close();
        connection.close();
    }

    @Test
    void shouldAckARealRequestedDeliveryAfterTheDeletionConsumerConfirmsIt() throws Exception {
        UUID documentId = UUID.randomUUID();
        DocumentDeletionScheduledMessage message = message(documentId);
        when(consumer.process(message)).thenReturn(
            DocumentDeletionMessageConsumer.ProcessingResult.acknowledged(documentId)
        );
        publishRequested(message);

        Channel channel = connectionFactory.createConnection().createChannel(false);
        GetResponse received = awaitBasicGet(channel, DocumentStorageRabbitTopology.DELETION_REQUESTED_QUEUE);
        assertNotNull(received);

        new RabbitMqDocumentDeletionMessageHandler(consumer).handle(
            message,
            delivery(received),
            channel
        );

        assertNull(channel.basicGet(DocumentStorageRabbitTopology.DELETION_REQUESTED_QUEUE, true));
        channel.close();
    }

    @Test
    void shouldRouteARealNackToTheDeletionDeadLetterQueue() throws Exception {
        UUID documentId = UUID.randomUUID();
        DocumentDeletionScheduledMessage message = message(documentId);
        when(consumer.process(message)).thenReturn(
            DocumentDeletionMessageConsumer.ProcessingResult.deadLetter(documentId)
        );
        publishRequested(message);

        Channel channel = connectionFactory.createConnection().createChannel(false);
        GetResponse received = awaitBasicGet(channel, DocumentStorageRabbitTopology.DELETION_REQUESTED_QUEUE);
        assertNotNull(received);

        new RabbitMqDocumentDeletionMessageHandler(consumer).handle(
            message,
            delivery(received),
            channel
        );

        assertNotNull(rabbitTemplate.receive(DocumentStorageRabbitTopology.DELETION_DEAD_LETTER_QUEUE, 5_000));
        channel.close();
    }

    private void publishRequested(DocumentDeletionScheduledMessage message) {
        rabbitTemplate.convertAndSend(
            DocumentStorageRabbitTopology.DELETION_EXCHANGE,
            DocumentStorageRabbitTopology.DELETION_REQUESTED_ROUTING_KEY,
            message
        );
    }

    private GetResponse awaitBasicGet(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            GetResponse received = channel.basicGet(queue, false);
            if (received != null) {
                return received;
            }
            Thread.sleep(25);
        }
        return null;
    }

    private DocumentDeletionScheduledMessage message(UUID documentId) {
        return new DocumentDeletionScheduledMessage(
            DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
            documentId
        );
    }

    private Message delivery(GetResponse received) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(received.getEnvelope().getDeliveryTag());
        return new Message(received.getBody(), properties);
    }
}
