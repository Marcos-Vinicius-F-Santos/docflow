package com.dockflow.dockflow.document.messaging.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageListenerContainer;
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
