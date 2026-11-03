package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.application.DocumentDeletionPublisher;
import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingReader;
import com.dockflow.dockflow.document.port.out.storage.DocumentStagingRemover;
import com.dockflow.dockflow.document.storage.DocumentStorage;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Declares the approved exchange, queues, retry delays and dead-letter route. */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentStorageRabbitConfiguration {

    @Bean
    MessageConverter documentStorageMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean(name = "rabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(DocumentStorageRabbitTopology.MIN_CONCURRENCY);
        factory.setMaxConcurrentConsumers(DocumentStorageRabbitTopology.MAX_CONCURRENCY);
        factory.setPrefetchCount(DocumentStorageRabbitTopology.PREFETCH_COUNT);
        return factory;
    }

    @Bean
    RabbitMqDocumentStorageRequestPublisher documentStorageRequestPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter
    ) {
        return new RabbitMqDocumentStorageRequestPublisher(
            rabbitTemplate,
            messageConverter,
            DocumentStorageRabbitTopology.PROCESSING_TIMEOUT
        );
    }

    @Bean
    DocumentDeletionPublisher documentDeletionPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter
    ) {
        return new RabbitMqDocumentDeletionPublisher(
            rabbitTemplate,
            messageConverter,
            DocumentStorageRabbitTopology.PROCESSING_TIMEOUT
        );
    }

    @Bean
    DocumentStorageMessageConsumer documentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage,
        Optional<DocumentDeletionPublisher> deletionPublisher,
        Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher,
        Optional<ApplicationLogRecorder> operationalLogRecorder
    ) {
        return new DocumentStorageMessageConsumer(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage,
            deletionPublisher,
            lifecycleEventPublisher,
            java.time.Clock.systemUTC(),
            operationalLogRecorder
        );
    }

    @Bean
    RabbitMqDocumentStorageMessageHandler documentStorageMessageHandler(
        DocumentStorageMessageConsumer consumer,
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter
    ) {
        return new RabbitMqDocumentStorageMessageHandler(
            consumer,
            rabbitTemplate,
            messageConverter,
            DocumentStorageRabbitTopology.PROCESSING_TIMEOUT
        );
    }

    @Bean
    DocumentDeletionMessageConsumer documentDeletionMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        DocumentStagingRemover stagingRemover,
        Optional<DocumentLifecycleEventPublisher> lifecycleEventPublisher
    ) {
        return new DocumentDeletionMessageConsumer(
            documentRepository,
            documentStorage,
            stagingRemover,
            lifecycleEventPublisher,
            java.time.Clock.systemUTC()
        );
    }

    @Bean
    RabbitMqDocumentDeletionMessageHandler documentDeletionMessageHandler(
        DocumentDeletionMessageConsumer consumer
    ) {
        return new RabbitMqDocumentDeletionMessageHandler(consumer);
    }

    @Bean
    DirectExchange documentStorageExchange() {
        return new DirectExchange(DocumentStorageRabbitTopology.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange documentStorageDeadLetterExchange() {
        return new DirectExchange(DocumentStorageRabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue documentStorageQueue() {
        return QueueBuilder.durable(DocumentStorageRabbitTopology.MAIN_QUEUE)
            .deadLetterExchange(DocumentStorageRabbitTopology.DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(DocumentStorageRabbitTopology.ROUTING_KEY)
            .build();
    }

    @Bean
    Binding documentStorageQueueBinding(Queue documentStorageQueue, DirectExchange documentStorageExchange) {
        return BindingBuilder.bind(documentStorageQueue)
            .to(documentStorageExchange)
            .with(DocumentStorageRabbitTopology.ROUTING_KEY);
    }

    @Bean
    Queue documentStorageDeadLetterQueue() {
        return QueueBuilder.durable(DocumentStorageRabbitTopology.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding documentStorageDeadLetterQueueBinding(
        Queue documentStorageDeadLetterQueue,
        DirectExchange documentStorageDeadLetterExchange
    ) {
        return BindingBuilder.bind(documentStorageDeadLetterQueue)
            .to(documentStorageDeadLetterExchange)
            .with(DocumentStorageRabbitTopology.ROUTING_KEY);
    }

    @Bean
    DirectExchange documentDeletionExchange() {
        return new DirectExchange(DocumentStorageRabbitTopology.DELETION_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange documentDeletionDeadLetterExchange() {
        return new DirectExchange(DocumentStorageRabbitTopology.DELETION_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue documentDeletionDelayQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", DocumentStorageRabbitTopology.DELETION_DELAY.toMillis());
        arguments.put("x-dead-letter-exchange", DocumentStorageRabbitTopology.DELETION_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", DocumentStorageRabbitTopology.DELETION_REQUESTED_ROUTING_KEY);
        return new Queue(
            DocumentStorageRabbitTopology.DELETION_DELAY_QUEUE,
            true,
            false,
            false,
            arguments
        );
    }

    @Bean
    Binding documentDeletionDelayQueueBinding(
        Queue documentDeletionDelayQueue,
        DirectExchange documentDeletionExchange
    ) {
        return BindingBuilder.bind(documentDeletionDelayQueue)
            .to(documentDeletionExchange)
            .with(DocumentStorageRabbitTopology.DELETION_SCHEDULED_ROUTING_KEY);
    }

    @Bean
    Queue documentDeletionRequestedQueue() {
        return QueueBuilder.durable(DocumentStorageRabbitTopology.DELETION_REQUESTED_QUEUE)
            .deadLetterExchange(DocumentStorageRabbitTopology.DELETION_DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(DocumentStorageRabbitTopology.DELETION_REQUESTED_ROUTING_KEY)
            .build();
    }

    @Bean
    Binding documentDeletionRequestedQueueBinding(
        Queue documentDeletionRequestedQueue,
        DirectExchange documentDeletionExchange
    ) {
        return BindingBuilder.bind(documentDeletionRequestedQueue)
            .to(documentDeletionExchange)
            .with(DocumentStorageRabbitTopology.DELETION_REQUESTED_ROUTING_KEY);
    }

    @Bean
    Queue documentDeletionDeadLetterQueue() {
        return QueueBuilder.durable(DocumentStorageRabbitTopology.DELETION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding documentDeletionDeadLetterQueueBinding(
        Queue documentDeletionDeadLetterQueue,
        DirectExchange documentDeletionDeadLetterExchange
    ) {
        return BindingBuilder.bind(documentDeletionDeadLetterQueue)
            .to(documentDeletionDeadLetterExchange)
            .with(DocumentStorageRabbitTopology.DELETION_REQUESTED_ROUTING_KEY);
    }

    @Bean
    Queue documentStorageRetry5sQueue() {
        return retryQueue(0);
    }

    @Bean
    Queue documentStorageRetry15sQueue() {
        return retryQueue(1);
    }

    @Bean
    Queue documentStorageRetry60sQueue() {
        return retryQueue(2);
    }

    private Queue retryQueue(int index) {
        DocumentStorageRabbitTopology.RetryQueue retry = DocumentStorageRabbitTopology.RETRY_QUEUES.get(index);
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", retry.delay().toMillis());
        arguments.put("x-dead-letter-exchange", DocumentStorageRabbitTopology.EXCHANGE);
        arguments.put("x-dead-letter-routing-key", DocumentStorageRabbitTopology.ROUTING_KEY);
        return new Queue(retry.name(), true, false, false, arguments);
    }
}
