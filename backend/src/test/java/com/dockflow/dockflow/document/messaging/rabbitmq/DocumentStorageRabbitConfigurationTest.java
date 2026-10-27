package com.dockflow.dockflow.document.messaging.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DocumentStorageRabbitConfigurationTest {

    @Test
    void shouldDeclareSingleConsumerAndPrefetchPolicyExplicitly() throws Exception {
        DocumentStorageRabbitConfiguration configuration = new DocumentStorageRabbitConfiguration();
        SimpleRabbitListenerContainerFactory factory = configuration.rabbitListenerContainerFactory(
            mock(ConnectionFactory.class),
            new Jackson2JsonMessageConverter()
        );
        RabbitListenerEndpoint endpoint = new RabbitListenerEndpoint() {
            @Override
            public String getId() {
                return "test";
            }

            @Override
            public String getGroup() {
                return null;
            }

            @Override
            public String getConcurrency() {
                return null;
            }

            @Override
            public Boolean getAutoStartup() {
                return false;
            }

            @Override
            public void setupListenerContainer(MessageListenerContainer container) {
            }

            @Override
            public Boolean getBatchListener() {
                return false;
            }
        };
        SimpleMessageListenerContainer container =
            (SimpleMessageListenerContainer) factory.createListenerContainer(endpoint);

        assertEquals(1, field(container, "concurrentConsumers"));
        assertEquals(1, field(container, "maxConcurrentConsumers"));
        assertEquals(1, field(container, "prefetchCount"));
    }

    @Test
    void shouldDeclareTheDurableThreeHourDeletionTopology() {
        DocumentStorageRabbitConfiguration configuration = new DocumentStorageRabbitConfiguration();

        Queue delay = configuration.documentDeletionDelayQueue();
        Queue requested = configuration.documentDeletionRequestedQueue();
        Queue deadLetter = configuration.documentDeletionDeadLetterQueue();

        assertEquals(DocumentStorageRabbitTopology.DELETION_DELAY_QUEUE, delay.getName());
        assertEquals(true, delay.isDurable());
        assertEquals(DocumentStorageRabbitTopology.DELETION_DELAY.toMillis(),
            delay.getArguments().get("x-message-ttl"));
        assertEquals(DocumentStorageRabbitTopology.DELETION_EXCHANGE,
            delay.getArguments().get("x-dead-letter-exchange"));
        assertEquals(DocumentStorageRabbitTopology.DELETION_REQUESTED_ROUTING_KEY,
            delay.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(DocumentStorageRabbitTopology.DELETION_REQUESTED_QUEUE, requested.getName());
        assertEquals(DocumentStorageRabbitTopology.DELETION_DEAD_LETTER_QUEUE, deadLetter.getName());
    }

    private int field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return ((Number) field.get(target)).intValue();
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
