package com.dockflow.dockflow.document.messaging.rabbitmq;

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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class RabbitMqRealIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static CachingConnectionFactory connectionFactory;
    private static RabbitAdmin admin;
    private static RabbitTemplate rabbitTemplate;
    private static MessageConverter messageConverter;

    @Mock
    private DocumentStorageMessageConsumer consumer;

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

        DocumentStorageRabbitConfiguration configuration = new DocumentStorageRabbitConfiguration();
        DirectTopology topology = new DirectTopology(configuration);
        topology.declare(admin);
    }

    @AfterAll
    static void closeRabbitConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void purgeQueues() {
        admin.purgeQueue(DocumentStorageRabbitTopology.MAIN_QUEUE, false);
        admin.purgeQueue(DocumentStorageRabbitTopology.DEAD_LETTER_QUEUE, false);
        DocumentStorageRabbitTopology.RETRY_QUEUES.forEach(queue -> admin.purgeQueue(queue.name(), false));
    }

    @Test
    void shouldPublishThroughTheRealExchangeAndReceivePublisherConfirmation() {
        UUID documentId = UUID.randomUUID();
        new RabbitMqDocumentStorageRequestPublisher(
            rabbitTemplate,
            messageConverter,
            Duration.ofSeconds(3)
        ).publish(new com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher.DocumentStorageRequested(
            1,
            documentId,
            "documents/" + documentId,
            "staging/" + documentId,
            8,
            "application/pdf"
        ));

        Message received = rabbitTemplate.receive(DocumentStorageRabbitTopology.MAIN_QUEUE, 5_000);
        assertNotNull(received);
        assertTrue(new String(received.getBody(), StandardCharsets.UTF_8).contains(documentId.toString()));
    }

    @Test
    void shouldConfirmRetryOnlyAfterRealRabbitPublicationAndReturnItToTheMainQueue() throws Exception {
        UUID documentId = UUID.randomUUID();
        DocumentStorageRequestedMessage message = new DocumentStorageRequestedMessage(
            1,
            documentId,
            "documents/" + documentId,
            "staging/" + documentId,
            8,
            "application/pdf"
        );
        when(consumer.process(message)).thenReturn(
            DocumentStorageMessageConsumer.ProcessingResult.retry(documentId)
        );

        Channel channel = org.mockito.Mockito.mock(Channel.class);
        Message delivery = delivery(1);
        new RabbitMqDocumentStorageMessageHandler(
            consumer,
            rabbitTemplate,
            messageConverter,
            Duration.ofSeconds(3)
        ).handle(message, delivery, channel);

        verify(channel).basicAck(1, false);
        Message returnedToMain = rabbitTemplate.receive(DocumentStorageRabbitTopology.MAIN_QUEUE, 8_000);
        assertNotNull(returnedToMain);
        assertTrue(returnedToMain.getMessageProperties().getHeaders().containsKey("x-docflow-retry-count"));
    }

    @Test
    void shouldRouteAnInvalidRealDeliveryDirectlyToTheDeadLetterQueue() throws Exception {
        when(consumer.process(null)).thenThrow(
            new DocumentStorageMessageValidationException("invalid message")
        );
        rabbitTemplate.convertAndSend(
            DocumentStorageRabbitTopology.EXCHANGE,
            DocumentStorageRabbitTopology.ROUTING_KEY,
            "invalid-json"
        );

        var connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        GetResponse received = channel.basicGet(DocumentStorageRabbitTopology.MAIN_QUEUE, false);
        assertNotNull(received);

        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(received.getEnvelope().getDeliveryTag());
        Message delivery = new Message(received.getBody(), properties);
        new RabbitMqDocumentStorageMessageHandler(
            consumer,
            rabbitTemplate,
            messageConverter,
            Duration.ofSeconds(3)
        ).handle(null, delivery, channel);

        Message deadLettered = rabbitTemplate.receive(DocumentStorageRabbitTopology.DEAD_LETTER_QUEUE, 5_000);
        assertNotNull(deadLettered);
        channel.close();
        connection.close();
    }

    private Message delivery(long tag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(tag);
        return new Message(new byte[0], properties);
    }

    private record DirectTopology(
        org.springframework.amqp.core.DirectExchange exchange,
        org.springframework.amqp.core.DirectExchange deadLetterExchange,
        Queue mainQueue,
        Queue deadLetterQueue,
        Binding mainBinding,
        Binding deadLetterBinding,
        Queue retry5s,
        Queue retry15s,
        Queue retry60s
    ) {
        private DirectTopology(DocumentStorageRabbitConfiguration configuration) {
            this(
                configuration.documentStorageExchange(),
                configuration.documentStorageDeadLetterExchange(),
                configuration.documentStorageQueue(),
                configuration.documentStorageDeadLetterQueue(),
                configuration.documentStorageQueueBinding(
                    configuration.documentStorageQueue(),
                    configuration.documentStorageExchange()
                ),
                configuration.documentStorageDeadLetterQueueBinding(
                    configuration.documentStorageDeadLetterQueue(),
                    configuration.documentStorageDeadLetterExchange()
                ),
                configuration.documentStorageRetry5sQueue(),
                configuration.documentStorageRetry15sQueue(),
                configuration.documentStorageRetry60sQueue()
            );
        }

        private void declare(RabbitAdmin admin) {
            admin.declareExchange(exchange);
            admin.declareExchange(deadLetterExchange);
            admin.declareQueue(mainQueue);
            admin.declareQueue(deadLetterQueue);
            admin.declareQueue(retry5s);
            admin.declareQueue(retry15s);
            admin.declareQueue(retry60s);
            admin.declareBinding(mainBinding);
            admin.declareBinding(deadLetterBinding);
        }
    }
}
