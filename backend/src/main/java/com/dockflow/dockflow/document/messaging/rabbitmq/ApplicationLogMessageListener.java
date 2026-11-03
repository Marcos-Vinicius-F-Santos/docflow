package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Connects the durable application-log queue to its manual-ACK handler. */
@Component
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ApplicationLogMessageListener {

    private final RabbitMqApplicationLogMessageHandler handler;

    public ApplicationLogMessageListener(RabbitMqApplicationLogMessageHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(
        queues = ApplicationLogRabbitTopology.MAIN_QUEUE,
        containerFactory = "rabbitListenerContainerFactory",
        concurrency = "1"
    )
    public void onMessage(ApplicationLogEvent event, Message delivery, Channel channel) throws IOException {
        handler.handle(event, delivery, channel);
    }
}
