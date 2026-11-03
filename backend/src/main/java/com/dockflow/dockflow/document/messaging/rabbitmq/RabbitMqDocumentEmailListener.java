package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Connects the e-mail queue to its retry/DLQ handler. */
@Component
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqDocumentEmailListener {

    private final RabbitMqDocumentEmailMessageHandler handler;

    public RabbitMqDocumentEmailListener(RabbitMqDocumentEmailMessageHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(
        queues = DocumentNotificationRabbitTopology.EMAIL_REQUESTED_QUEUE,
        containerFactory = "rabbitListenerContainerFactory",
        concurrency = DocumentStorageRabbitTopology.LISTENER_CONCURRENCY
    )
    public void onMessage(DocumentEmailNotificationMessage message, Message delivery, Channel channel)
        throws IOException {
        handler.handle(message, delivery, channel);
    }
}
