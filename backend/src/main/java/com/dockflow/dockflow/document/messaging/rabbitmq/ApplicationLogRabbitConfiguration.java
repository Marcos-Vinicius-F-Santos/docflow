package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogPublisher;
import com.dockflow.dockflow.document.application.logging.ApplicationLogPersistenceService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/** Declares the durable operational-log topology from ADR-009. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ApplicationLogRabbitConfiguration {

    @Bean
    ApplicationLogPublisher applicationLogPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter
    ) {
        return new RabbitMqApplicationLogPublisher(
            rabbitTemplate,
            messageConverter,
            ApplicationLogRabbitTopology.PUBLISH_CONFIRMATION_TIMEOUT
        );
    }

    @Bean
    ApplicationLogMessageConsumer applicationLogMessageConsumer(
        ApplicationLogPersistenceService persistenceService
    ) {
        return new ApplicationLogMessageConsumer(persistenceService);
    }

    @Bean
    RabbitMqApplicationLogMessageHandler applicationLogMessageHandler(
        ApplicationLogMessageConsumer consumer,
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter
    ) {
        return new RabbitMqApplicationLogMessageHandler(
            consumer,
            rabbitTemplate,
            messageConverter,
            ApplicationLogRabbitTopology.PUBLISH_CONFIRMATION_TIMEOUT
        );
    }

    @Bean
    TopicExchange applicationLogExchange() {
        return new TopicExchange(ApplicationLogRabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    TopicExchange applicationLogDeadLetterExchange() {
        return new TopicExchange(ApplicationLogRabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue applicationLogMainQueue() {
        return QueueBuilder.durable(ApplicationLogRabbitTopology.MAIN_QUEUE)
            .deadLetterExchange(ApplicationLogRabbitTopology.DEAD_LETTER_EXCHANGE)
            .build();
    }

    @Bean
    Binding applicationLogInfoBinding(
        @Qualifier("applicationLogMainQueue") Queue queue,
        @Qualifier("applicationLogExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(ApplicationLogRabbitTopology.INFO_ROUTING_KEY);
    }

    @Bean
    Binding applicationLogWarnBinding(
        @Qualifier("applicationLogMainQueue") Queue queue,
        @Qualifier("applicationLogExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(ApplicationLogRabbitTopology.WARN_ROUTING_KEY);
    }

    @Bean
    Binding applicationLogErrorBinding(
        @Qualifier("applicationLogMainQueue") Queue queue,
        @Qualifier("applicationLogExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(ApplicationLogRabbitTopology.ERROR_ROUTING_KEY);
    }

    @Bean
    Queue applicationLogDeadLetterQueue() {
        return QueueBuilder.durable(ApplicationLogRabbitTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding applicationLogDeadLetterBinding(
        @Qualifier("applicationLogDeadLetterQueue") Queue queue,
        @Qualifier("applicationLogDeadLetterExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with("#");
    }

    @Bean
    Queue applicationLogRetry5sQueue() {
        return retryQueue(0);
    }

    @Bean
    Queue applicationLogRetry15sQueue() {
        return retryQueue(1);
    }

    @Bean
    Queue applicationLogRetry60sQueue() {
        return retryQueue(2);
    }

    @Bean
    MessagePostProcessor applicationLogPersistentMessagePostProcessor() {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType("application/json");
            return message;
        };
    }

    private Queue retryQueue(int index) {
        ApplicationLogRabbitTopology.RetryQueue retry = ApplicationLogRabbitTopology.RETRY_QUEUES.get(index);
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", retry.delay().toMillis());
        arguments.put("x-dead-letter-exchange", ApplicationLogRabbitTopology.EXCHANGE);
        arguments.put("x-dead-letter-routing-key", ApplicationLogRabbitTopology.RETRY_ROUTING_KEY);
        return new Queue(retry.name(), true, false, false, arguments);
    }
}
