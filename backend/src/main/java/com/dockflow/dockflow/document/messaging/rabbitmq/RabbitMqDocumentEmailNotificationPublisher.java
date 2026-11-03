package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentNotificationDispatcher;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Publishes an e-mail command without coupling document processing to the provider. */
public class RabbitMqDocumentEmailNotificationPublisher implements DocumentNotificationDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final String recipient;
    private final Duration confirmationTimeout;

    public RabbitMqDocumentEmailNotificationPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        String recipient,
        Duration confirmationTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.recipient = recipient;
        this.confirmationTimeout = confirmationTimeout;
    }

    @Override
    public void dispatch(DocumentLifecycleEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        DocumentEmailNotificationMessage command = new DocumentEmailNotificationMessage(
            DocumentEmailNotificationMessage.SCHEMA_VERSION,
            event,
            recipient
        );
        CorrelationData correlation = new CorrelationData("email-command-" + event.eventId());
        try {
            Message outbound = messageConverter.toMessage(command, new MessageProperties());
            outbound = persistentJson().postProcessMessage(outbound);
            rabbitTemplate.send(
                DocumentNotificationRabbitTopology.EVENTS_EXCHANGE,
                DocumentNotificationRabbitTopology.EMAIL_ROUTING_KEY,
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
                    event.documentId(),
                    "RabbitMQ did not confirm e-mail notification publication: " + reason
                );
            }
        } catch (DocumentPublicationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DocumentPublicationException(
                event.documentId(),
                "E-mail notification publication confirmation was interrupted",
                exception
            );
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw new DocumentPublicationException(
                event.documentId(),
                "E-mail notification publication was not confirmed",
                exception
            );
        }
    }

    private MessagePostProcessor persistentJson() {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType(DocumentNotificationRabbitTopology.JSON_CONTENT_TYPE);
            return message;
        };
    }
}
