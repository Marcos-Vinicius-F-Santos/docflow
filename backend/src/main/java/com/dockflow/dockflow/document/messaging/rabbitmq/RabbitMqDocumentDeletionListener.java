package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Connects the delayed deletion queue to its provider-neutral handler. */
@Component
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqDocumentDeletionListener {

    private final RabbitMqDocumentDeletionMessageHandler handler;

    public RabbitMqDocumentDeletionListener(RabbitMqDocumentDeletionMessageHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(
        queues = DocumentStorageRabbitTopology.DELETION_REQUESTED_QUEUE,
        containerFactory = "rabbitListenerContainerFactory",
        concurrency = DocumentStorageRabbitTopology.LISTENER_CONCURRENCY
    )
    public void onMessage(
        DocumentDeletionScheduledMessage message,
        Message delivery,
        Channel channel
    ) throws IOException {
        handler.handle(message, delivery, channel);
    }
}
