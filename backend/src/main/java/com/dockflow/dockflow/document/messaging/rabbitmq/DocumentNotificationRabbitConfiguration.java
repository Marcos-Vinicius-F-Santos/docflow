package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.DocumentNotificationDispatcher;
import com.dockflow.dockflow.document.port.out.email.DocumentEmailSender;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Declares the separate durable notification topology from ADR-008. */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentNotificationRabbitConfiguration {

    @Bean
    DocumentLifecycleEventPublisher documentLifecycleEventPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter
    ) {
        return new RabbitMqDocumentLifecycleEventPublisher(
            rabbitTemplate,
            messageConverter,
            DocumentStorageRabbitTopology.PROCESSING_TIMEOUT
        );
    }

    @Bean
    DocumentNotificationMessageConsumer documentNotificationMessageConsumer(
        Optional<DocumentNotificationDispatcher> dispatcher,
        Optional<ApplicationLogRecorder> operationalLogRecorder
    ) {
        return new DocumentNotificationMessageConsumer(dispatcher, operationalLogRecorder);
    }

    @Bean
    RabbitMqDocumentNotificationMessageHandler documentNotificationMessageHandler(
        DocumentNotificationMessageConsumer consumer
    ) {
        return new RabbitMqDocumentNotificationMessageHandler(consumer);
    }

    @Bean
    DocumentEmailMessageConsumer documentEmailMessageConsumer(
        Optional<DocumentEmailSender> sender,
        Optional<ApplicationLogRecorder> operationalLogRecorder
    ) {
        return new DocumentEmailMessageConsumer(sender, operationalLogRecorder);
    }

    @Bean
    RabbitMqDocumentEmailMessageHandler documentEmailMessageHandler(
        DocumentEmailMessageConsumer consumer,
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter
    ) {
        return new RabbitMqDocumentEmailMessageHandler(
            consumer,
            rabbitTemplate,
            messageConverter,
            DocumentStorageRabbitTopology.PROCESSING_TIMEOUT
        );
    }

    @Bean
    TopicExchange documentNotificationEventsExchange() {
        return new TopicExchange(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange documentNotificationDeadLetterExchange() {
        return new TopicExchange(
            DocumentNotificationRabbitTopology.NOTIFICATIONS_DEAD_LETTER_EXCHANGE,
            true,
            false
        );
    }

    @Bean
    TopicExchange documentEmailNotificationDeadLetterExchange() {
        return new TopicExchange(
            DocumentNotificationRabbitTopology.EMAIL_DEAD_LETTER_EXCHANGE,
            true,
            false
        );
    }

    @Bean
    Queue documentNotificationRequestedQueue() {
        return QueueBuilder.durable(DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE)
            .deadLetterExchange(DocumentNotificationRabbitTopology.NOTIFICATIONS_DEAD_LETTER_EXCHANGE)
            .build();
    }

    @Bean
    Binding documentNotificationProcessingStartedBinding(
        @Qualifier("documentNotificationRequestedQueue") Queue queue,
        @Qualifier("documentNotificationEventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange)
            .with(DocumentNotificationRabbitTopology.PROCESSING_STARTED_ROUTING_KEY);
    }

    @Bean
    Binding documentNotificationCompletedBinding(
        @Qualifier("documentNotificationRequestedQueue") Queue queue,
        @Qualifier("documentNotificationEventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange)
            .with(DocumentNotificationRabbitTopology.COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding documentNotificationFailedBinding(
        @Qualifier("documentNotificationRequestedQueue") Queue queue,
        @Qualifier("documentNotificationEventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange)
            .with(DocumentNotificationRabbitTopology.FAILED_ROUTING_KEY);
    }

    @Bean
    Binding documentNotificationDeletionWarningBinding(
        @Qualifier("documentNotificationRequestedQueue") Queue queue,
        @Qualifier("documentNotificationEventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange)
            .with(DocumentNotificationRabbitTopology.DELETION_WARNING_ROUTING_KEY);
    }

    @Bean
    Binding documentNotificationDeletedBinding(
        @Qualifier("documentNotificationRequestedQueue") Queue queue,
        @Qualifier("documentNotificationEventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange)
            .with(DocumentNotificationRabbitTopology.DELETED_ROUTING_KEY);
    }

    @Bean
    Queue documentNotificationDeletionWarningDelayQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(
            "x-message-ttl",
            DocumentNotificationRabbitTopology.DELETION_WARNING_DELAY.toMillis()
        );
        arguments.put("x-dead-letter-exchange", DocumentNotificationRabbitTopology.EVENTS_EXCHANGE);
        arguments.put(
            "x-dead-letter-routing-key",
            DocumentNotificationRabbitTopology.DELETION_WARNING_ROUTING_KEY
        );
        return new Queue(
            DocumentNotificationRabbitTopology.DELETION_WARNING_DELAY_QUEUE,
            true,
            false,
            false,
            arguments
        );
    }

    @Bean
    Binding documentNotificationDeletionWarningDelayBinding(
        @Qualifier("documentNotificationDeletionWarningDelayQueue") Queue queue,
        @Qualifier("documentNotificationEventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange)
            .with(DocumentNotificationRabbitTopology.DELETION_WARNING_SCHEDULED_ROUTING_KEY);
    }

    @Bean
    Queue documentEmailNotificationRequestedQueue() {
        return QueueBuilder.durable(DocumentNotificationRabbitTopology.EMAIL_REQUESTED_QUEUE)
            .deadLetterExchange(DocumentNotificationRabbitTopology.EMAIL_DEAD_LETTER_EXCHANGE)
            .build();
    }

    @Bean
    Binding documentEmailNotificationRequestedBinding(
        @Qualifier("documentEmailNotificationRequestedQueue") Queue queue,
        @Qualifier("documentNotificationEventsExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange)
            .with(DocumentNotificationRabbitTopology.EMAIL_ROUTING_KEY);
    }

    @Bean
    Queue documentNotificationDeadLetterQueue() {
        return QueueBuilder.durable(DocumentNotificationRabbitTopology.NOTIFICATIONS_DEAD_LETTER_QUEUE)
            .build();
    }

    @Bean
    Binding documentNotificationDeadLetterBinding(
        @Qualifier("documentNotificationDeadLetterQueue") Queue queue,
        @Qualifier("documentNotificationDeadLetterExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with("#");
    }

    @Bean
    Queue documentEmailNotificationDeadLetterQueue() {
        return QueueBuilder.durable(DocumentNotificationRabbitTopology.EMAIL_DEAD_LETTER_QUEUE)
            .build();
    }

    @Bean
    Binding documentEmailNotificationDeadLetterBinding(
        @Qualifier("documentEmailNotificationDeadLetterQueue") Queue queue,
        @Qualifier("documentEmailNotificationDeadLetterExchange") TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with("#");
    }

    @Bean
    Queue documentEmailNotificationRetry30sQueue() {
        return emailRetryQueue(0);
    }

    @Bean
    Queue documentEmailNotificationRetry5mQueue() {
        return emailRetryQueue(1);
    }

    @Bean
    Queue documentEmailNotificationRetry30mQueue() {
        return emailRetryQueue(2);
    }

    @Bean
    MessagePostProcessor documentNotificationPersistentMessagePostProcessor() {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType(DocumentNotificationRabbitTopology.JSON_CONTENT_TYPE);
            return message;
        };
    }

    private Queue emailRetryQueue(int index) {
        DocumentNotificationRabbitTopology.RetryQueue retry =
            DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.get(index);
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", retry.delay().toMillis());
        arguments.put("x-dead-letter-exchange", DocumentNotificationRabbitTopology.EVENTS_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", DocumentNotificationRabbitTopology.EMAIL_ROUTING_KEY);
        return new Queue(retry.name(), true, false, false, arguments);
    }
}
