package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.util.UUID;

/** RabbitMQ acknowledgment boundary for delayed document deletion. */
public class RabbitMqDocumentDeletionMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqDocumentDeletionMessageHandler.class);

    private final DocumentDeletionMessageConsumer consumer;

    public RabbitMqDocumentDeletionMessageHandler(DocumentDeletionMessageConsumer consumer) {
        this.consumer = consumer;
    }

    public void handle(DocumentDeletionScheduledMessage message, Message delivery, Channel channel) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        UUID documentId = message == null ? null : message.documentId();
        try {
            DocumentDeletionMessageConsumer.ProcessingResult result = consumer.process(message);
            if (result.disposition() == DocumentDeletionMessageConsumer.Disposition.ACKNOWLEDGE) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            channel.basicNack(deliveryTag, false, false);
        } catch (DocumentDeletionMessageValidationException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("invalid document deletion message sent to dead letter");
            channel.basicNack(deliveryTag, false, false);
        } catch (RuntimeException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("document deletion failed; sent to dead letter");
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
