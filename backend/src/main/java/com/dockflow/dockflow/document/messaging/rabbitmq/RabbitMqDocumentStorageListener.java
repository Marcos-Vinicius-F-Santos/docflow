package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Connects the approved main queue to the provider-neutral consumer handler. */
@Component
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqDocumentStorageListener {

    private final RabbitMqDocumentStorageMessageHandler handler;

    public RabbitMqDocumentStorageListener(RabbitMqDocumentStorageMessageHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(
        queues = DocumentStorageRabbitTopology.MAIN_QUEUE,
        containerFactory = "rabbitListenerContainerFactory",
        concurrency = DocumentStorageRabbitTopology.LISTENER_CONCURRENCY
    )
    public void onMessage(
        DocumentStorageRequestedMessage message,
        Message delivery,
        Channel channel
    ) throws IOException {
        handler.handle(message, delivery, channel);
    }
}
