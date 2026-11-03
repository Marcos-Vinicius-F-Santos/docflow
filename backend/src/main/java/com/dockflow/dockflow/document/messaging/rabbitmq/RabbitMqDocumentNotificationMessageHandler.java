package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.util.UUID;

/** RabbitMQ acknowledgment boundary for lifecycle notification dispatch. */
public class RabbitMqDocumentNotificationMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqDocumentNotificationMessageHandler.class);

    private final DocumentNotificationMessageConsumer consumer;

    public RabbitMqDocumentNotificationMessageHandler(DocumentNotificationMessageConsumer consumer) {
        this.consumer = consumer;
    }

    public void handle(DocumentLifecycleEvent event, Message delivery, Channel channel) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        UUID eventId = event == null ? null : event.eventId();
        try {
            DocumentNotificationMessageConsumer.ProcessingResult result = consumer.process(event);
            if (result.disposition() == DocumentNotificationMessageConsumer.Disposition.ACKNOWLEDGE) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            channel.basicReject(deliveryTag, false);
        } catch (DocumentNotificationMessageValidationException exception) {
            log.atWarn().addKeyValue("eventId", eventId).setCause(exception)
                .log("invalid document notification sent to dead letter");
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.atWarn().addKeyValue("eventId", eventId).setCause(exception)
                .log("document notification failed; sent to dead letter");
            channel.basicReject(deliveryTag, false);
        }
    }
}
