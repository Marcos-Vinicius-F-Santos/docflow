package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentLifecycleEventPublisher;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** RabbitMQ adapter for provider-neutral lifecycle event publication. */
public final class RabbitMqDocumentLifecycleEventPublisher implements DocumentLifecycleEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Duration confirmationTimeout;

    public RabbitMqDocumentLifecycleEventPublisher(RabbitTemplate rabbitTemplate) {
        this(
            rabbitTemplate,
            new Jackson2JsonMessageConverter(),
            DocumentStorageRabbitTopology.PROCESSING_TIMEOUT
        );
    }

    public RabbitMqDocumentLifecycleEventPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        Duration confirmationTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.confirmationTimeout = confirmationTimeout;
    }

    @Override
    public void publish(DocumentLifecycleEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        publish(event, routingKey(event.type()));
    }

    @Override
    public void scheduleDeletionWarning(DocumentLifecycleEvent event) {
        publish(event, DocumentNotificationRabbitTopology.DELETION_WARNING_SCHEDULED_ROUTING_KEY);
    }

    private void publish(DocumentLifecycleEvent event, String routingKey) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        UUID documentId = event.documentId();
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        try {
            Message outbound = messageConverter.toMessage(event, new MessageProperties());
            outbound = persistentJsonMessage().postProcessMessage(outbound);
            rabbitTemplate.send(
                DocumentNotificationRabbitTopology.EVENTS_EXCHANGE,
                routingKey,
                outbound,
                correlation
            );

            CorrelationData.Confirm confirm = correlation.getFuture().get(
                confirmationTimeout.toMillis(),
                TimeUnit.MILLISECONDS
            );
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "no publisher confirmation" : confirm.getReason();
                throw new DocumentPublicationException(
                    documentId,
                    "RabbitMQ did not confirm lifecycle event publication: " + reason
                );
            }
        } catch (DocumentPublicationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DocumentPublicationException(
                documentId,
                "Lifecycle event publication confirmation was interrupted",
                exception
            );
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw new DocumentPublicationException(
                documentId,
                "Lifecycle event publication was not confirmed",
                exception
            );
        }
    }

    static String routingKey(DocumentLifecycleEventType type) {
        return switch (type) {
            case DOCUMENT_PROCESSING_STARTED -> DocumentNotificationRabbitTopology.PROCESSING_STARTED_ROUTING_KEY;
            case DOCUMENT_COMPLETED -> DocumentNotificationRabbitTopology.COMPLETED_ROUTING_KEY;
            case DOCUMENT_FAILED -> DocumentNotificationRabbitTopology.FAILED_ROUTING_KEY;
            case DOCUMENT_DELETION_WARNING -> DocumentNotificationRabbitTopology.DELETION_WARNING_ROUTING_KEY;
            case DOCUMENT_DELETED -> DocumentNotificationRabbitTopology.DELETED_ROUTING_KEY;
        };
    }

    private MessagePostProcessor persistentJsonMessage() {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            properties.setContentType(DocumentNotificationRabbitTopology.JSON_CONTENT_TYPE);
            return message;
        };
    }
}
