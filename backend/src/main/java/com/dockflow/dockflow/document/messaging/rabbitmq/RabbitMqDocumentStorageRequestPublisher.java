package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentPublicationException;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/** RabbitMQ adapter for the provider-neutral publication port. */
public class RabbitMqDocumentStorageRequestPublisher implements DocumentStorageRequestPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqDocumentStorageRequestPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Duration confirmationTimeout;

    public RabbitMqDocumentStorageRequestPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, new Jackson2JsonMessageConverter(), DocumentStorageRabbitTopology.PROCESSING_TIMEOUT);
    }

    public RabbitMqDocumentStorageRequestPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        Duration confirmationTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.confirmationTimeout = confirmationTimeout;
    }

    @Override
    public void publish(DocumentStorageRequested request) {
        UUID documentId = request.documentId();
        log.atInfo().addKeyValue("documentId", documentId).log("document storage publication started");

        DocumentStorageRequestedMessage message = new DocumentStorageRequestedMessage(
            request.schemaVersion(),
            request.documentId(),
            request.objectKey(),
            request.contentReference(),
            request.sizeBytes(),
            request.contentType()
        );
        CorrelationData correlation = new CorrelationData(documentId.toString());

        try {
            Message outbound = messageConverter.toMessage(message, new MessageProperties());
            outbound = persistentMessage().postProcessMessage(outbound);
            rabbitTemplate.send(
                DocumentStorageRabbitTopology.EXCHANGE,
                DocumentStorageRabbitTopology.ROUTING_KEY,
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
                    "RabbitMQ did not confirm document publication: " + reason
                );
            }

            log.atInfo().addKeyValue("documentId", documentId).log("document storage publication confirmed");
        } catch (DocumentPublicationException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("document storage publication failed");
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw publicationFailure(documentId, "RabbitMQ publication confirmation was interrupted", exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw publicationFailure(documentId, "RabbitMQ publication was not confirmed", exception);
        }
    }

    private MessagePostProcessor persistentMessage() {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            properties.setContentType("application/json");
            return message;
        };
    }

    private DocumentPublicationException publicationFailure(UUID documentId, String message, Throwable cause) {
        log.atWarn().addKeyValue("documentId", documentId).setCause(cause)
            .log("document storage publication failed");
        return new DocumentPublicationException(documentId, message, cause);
    }
}
