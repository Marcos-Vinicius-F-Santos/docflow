package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogPersistenceService;
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
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class ApplicationLogRabbitRealIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static CachingConnectionFactory connectionFactory;
    private static RabbitAdmin admin;
    private static RabbitTemplate rabbitTemplate;
    private static MessageConverter messageConverter;

    @Mock
    private ApplicationLogMessageConsumer consumer;

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
        declareTopology(new ApplicationLogRabbitConfiguration());
    }

    @AfterAll
    static void closeRabbitConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void purgeQueues() {
        admin.purgeQueue(ApplicationLogRabbitTopology.MAIN_QUEUE, false);
        admin.purgeQueue(ApplicationLogRabbitTopology.DEAD_LETTER_QUEUE, false);
        ApplicationLogRabbitTopology.RETRY_QUEUES.forEach(queue -> admin.purgeQueue(queue.name(), false));
    }

    @Test
    void shouldPublishPersistentLogThroughTheRealExchangeWithConfirmation() throws Exception {
        ApplicationLogEvent event = event();

        new RabbitMqApplicationLogPublisher(
            rabbitTemplate,
            messageConverter,
            Duration.ofSeconds(3)
        ).publish(event);

        try (var connection = connectionFactory.createConnection(); Channel channel = connection.createChannel(false)) {
            GetResponse received = awaitDelivery(channel, ApplicationLogRabbitTopology.MAIN_QUEUE, Duration.ofSeconds(3));
            assertNotNull(received);
            assertEquals(2, received.getProps().getDeliveryMode());
            assertEquals(1, received.getProps().getHeaders().get("schemaVersion"));
            assertTrue(new String(received.getBody(), StandardCharsets.UTF_8).contains(event.eventId().toString()));
            channel.basicAck(received.getEnvelope().getDeliveryTag(), false);
        }
    }

    @Test
    void shouldAckOnlyAfterRealDeliveryIsProcessedAndScheduleARealRetry() throws Exception {
        ApplicationLogEvent event = event();
        publish(event);
        when(consumer.process(event)).thenThrow(new IllegalStateException("database unavailable"));

        try (var connection = connectionFactory.createConnection(); Channel channel = connection.createChannel(false)) {
            GetResponse received = awaitDelivery(channel, ApplicationLogRabbitTopology.MAIN_QUEUE, Duration.ofSeconds(3));
            assertNotNull(received);
            Message delivery = delivery(received);
            new RabbitMqApplicationLogMessageHandler(
                consumer,
                rabbitTemplate,
                messageConverter,
                Duration.ofSeconds(3)
            ).handle(event, delivery, channel);
        }

        Message retry = rabbitTemplate.receive(ApplicationLogRabbitTopology.RETRY_QUEUES.get(0).name(), 5_000);
        assertNotNull(retry);
        assertEquals(1, retry.getMessageProperties().getHeaders().get(
            RabbitMqApplicationLogMessageHandler.RETRY_COUNT_HEADER
        ));
        verify(consumer).process(event);
    }

    @Test
    void shouldRouteAnInvalidRealDeliveryToTheDedicatedDeadLetterQueue() throws Exception {
        publishRaw("invalid-log".getBytes(StandardCharsets.UTF_8));
        when(consumer.process(null)).thenThrow(new ApplicationLogMessageValidationException("invalid message"));

        try (var connection = connectionFactory.createConnection(); Channel channel = connection.createChannel(false)) {
            GetResponse received = awaitDelivery(channel, ApplicationLogRabbitTopology.MAIN_QUEUE, Duration.ofSeconds(3));
            assertNotNull(received);
            new RabbitMqApplicationLogMessageHandler(
                consumer,
                rabbitTemplate,
                messageConverter,
                Duration.ofSeconds(3)
            ).handle(null, delivery(received), channel);
        }

        assertNotNull(rabbitTemplate.receive(ApplicationLogRabbitTopology.DEAD_LETTER_QUEUE, 5_000));
    }

    private static void publish(ApplicationLogEvent event) {
        new RabbitMqApplicationLogPublisher(rabbitTemplate, messageConverter, Duration.ofSeconds(3)).publish(event);
    }

    private static void publishRaw(byte[] payload) throws Exception {
        CorrelationData correlation = new CorrelationData("raw-log-" + UUID.randomUUID());
        rabbitTemplate.send(
            ApplicationLogRabbitTopology.EXCHANGE,
            ApplicationLogRabbitTopology.INFO_ROUTING_KEY,
            new Message(payload, new MessageProperties()),
            correlation
        );
        CorrelationData.Confirm confirmation = correlation.getFuture().get(3, TimeUnit.SECONDS);
        assertNotNull(confirmation);
        assertTrue(confirmation.isAck());
    }

    private static Message delivery(GetResponse received) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(received.getEnvelope().getDeliveryTag());
        return new Message(received.getBody(), properties);
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

    private static ApplicationLogEvent event() {
        return new ApplicationLogEvent(
            UUID.randomUUID(),
            1,
            Instant.parse("2026-10-29T12:00:00Z"),
            ApplicationLogLevel.INFO,
            "document-api",
            "storage",
            "document.processing",
            "Document storage completed",
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of()
        );
    }

    private static void declareTopology(ApplicationLogRabbitConfiguration configuration) {
        TopicExchange exchange = configuration.applicationLogExchange();
        TopicExchange deadLetterExchange = configuration.applicationLogDeadLetterExchange();
        Queue mainQueue = configuration.applicationLogMainQueue();
        Queue deadLetterQueue = configuration.applicationLogDeadLetterQueue();
        admin.declareExchange(exchange);
        admin.declareExchange(deadLetterExchange);
        admin.declareQueue(mainQueue);
        admin.declareQueue(deadLetterQueue);
        admin.declareQueue(configuration.applicationLogRetry5sQueue());
        admin.declareQueue(configuration.applicationLogRetry15sQueue());
        admin.declareQueue(configuration.applicationLogRetry60sQueue());
        admin.declareBinding(configuration.applicationLogInfoBinding(mainQueue, exchange));
        admin.declareBinding(configuration.applicationLogWarnBinding(mainQueue, exchange));
        admin.declareBinding(configuration.applicationLogErrorBinding(mainQueue, exchange));
        admin.declareBinding(configuration.applicationLogDeadLetterBinding(deadLetterQueue, deadLetterExchange));
    }
}
