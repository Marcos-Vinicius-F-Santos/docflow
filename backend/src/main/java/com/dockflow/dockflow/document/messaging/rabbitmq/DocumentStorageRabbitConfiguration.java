package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.DocumentRepository;
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
    DocumentStorageMessageConsumer documentStorageMessageConsumer(
        DocumentRepository documentRepository,
        DocumentStagingReader stagingReader,
        DocumentStagingRemover stagingRemover,
        DocumentStorage documentStorage
    ) {
        return new DocumentStorageMessageConsumer(
            documentRepository,
            stagingReader,
            stagingRemover,
            documentStorage
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
