package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Connects the notification queue to the provider-neutral dispatch handler. */
@Component
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqDocumentNotificationListener {

    private final RabbitMqDocumentNotificationMessageHandler handler;

    public RabbitMqDocumentNotificationListener(RabbitMqDocumentNotificationMessageHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(
        queues = DocumentNotificationRabbitTopology.NOTIFICATIONS_REQUESTED_QUEUE,
        containerFactory = "rabbitListenerContainerFactory",
        concurrency = DocumentStorageRabbitTopology.LISTENER_CONCURRENCY
    )
    public void onMessage(DocumentLifecycleEvent event, Message delivery, Channel channel) throws IOException {
        handler.handle(event, delivery, channel);
    }
}
