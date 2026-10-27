package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentDeletionPublisher;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
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

/** RabbitMQ adapter for the delayed document deletion publisher port. */
public class RabbitMqDocumentDeletionPublisher implements DocumentDeletionPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqDocumentDeletionPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Duration confirmationTimeout;

    public RabbitMqDocumentDeletionPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, new Jackson2JsonMessageConverter(), DocumentStorageRabbitTopology.PROCESSING_TIMEOUT);
    }

    public RabbitMqDocumentDeletionPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        Duration confirmationTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.confirmationTimeout = confirmationTimeout;
    }

    @Override
    public void publish(DocumentDeletionScheduled request) {
        UUID documentId = request.documentId();
        DocumentDeletionScheduledMessage message = new DocumentDeletionScheduledMessage(
            request.schemaVersion(),
            request.documentId()
        );
        CorrelationData correlation = new CorrelationData("document-deletion-" + documentId);
        log.atInfo().addKeyValue("documentId", documentId).log("document deletion scheduling started");

        try {
            Message outbound = messageConverter.toMessage(message, new MessageProperties());
            outbound = persistentJson().postProcessMessage(outbound);
            rabbitTemplate.send(
                DocumentStorageRabbitTopology.DELETION_EXCHANGE,
                DocumentStorageRabbitTopology.DELETION_SCHEDULED_ROUTING_KEY,
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
                    "RabbitMQ did not confirm document deletion scheduling: " + reason
                );
            }
            log.atInfo().addKeyValue("documentId", documentId).log("document deletion scheduling confirmed");
        } catch (DocumentPublicationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw publicationFailure(documentId, "Deletion scheduling confirmation was interrupted", exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw publicationFailure(documentId, "Document deletion scheduling was not confirmed", exception);
        }
    }

    private MessagePostProcessor persistentJson() {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType("application/json");
            return message;
        };
    }

    private DocumentPublicationException publicationFailure(UUID documentId, String message, Throwable cause) {
        log.atWarn().addKeyValue("documentId", documentId).setCause(cause)
            .log("document deletion scheduling failed");
        return new DocumentPublicationException(documentId, message, cause);
    }
}
