package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentStorageMessageConsumer.Disposition;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentStorageMessageConsumer.ProcessingResult;
import com.rabbitmq.client.Channel;
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

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** RabbitMQ boundary for acknowledgment, retry routing and dead-lettering. */
public class RabbitMqDocumentStorageMessageHandler {

    private static final String RETRY_COUNT_HEADER = "x-docflow-retry-count";
    private static final Logger log = LoggerFactory.getLogger(RabbitMqDocumentStorageMessageHandler.class);

    private final DocumentStorageMessageConsumer consumer;
    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Duration confirmationTimeout;

    public RabbitMqDocumentStorageMessageHandler(
        DocumentStorageMessageConsumer consumer,
        RabbitTemplate rabbitTemplate
    ) {
        this(consumer, rabbitTemplate, new Jackson2JsonMessageConverter(), DocumentStorageRabbitTopology.PROCESSING_TIMEOUT);
    }

    public RabbitMqDocumentStorageMessageHandler(
        DocumentStorageMessageConsumer consumer,
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        Duration confirmationTimeout
    ) {
        this.consumer = consumer;
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.confirmationTimeout = confirmationTimeout;
    }

    public void handle(DocumentStorageRequestedMessage message, Message delivery, Channel channel) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        UUID documentId = message == null ? null : message.documentId();

        try {
            ProcessingResult result = consumer.process(message);
            if (result.disposition() == Disposition.ACKNOWLEDGE) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (result.disposition() == Disposition.DEAD_LETTER) {
                channel.basicReject(deliveryTag, false);
                return;
            }

            routeRetryOrDeadLetter(message, delivery, channel, result);
        } catch (DocumentStorageMessageValidationException exception) {
            log.atWarn().addKeyValue("documentId", documentId).setCause(exception)
                .log("invalid document storage message sent to dead letter");
            channel.basicReject(deliveryTag, false);
        }
    }

    private void routeRetryOrDeadLetter(
        DocumentStorageRequestedMessage message,
        Message delivery,
        Channel channel,
        ProcessingResult result
    ) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        int retryCount = retryCount(delivery.getMessageProperties());
        if (retryCount >= DocumentStorageRabbitTopology.RETRY_QUEUES.size()) {
            if (result.shouldMarkFailedOnRetryExhaustion()) {
                consumer.markFailedAfterPermanentFailure(result.documentId());
            }
            channel.basicReject(deliveryTag, false);
            log.atWarn().addKeyValue("documentId", message.documentId())
                .log("document storage retries exhausted; sent to dead letter");
            return;
        }

        String retryQueue = DocumentStorageRabbitTopology.RETRY_QUEUES.get(retryCount).name();
        CorrelationData correlation = new CorrelationData(message.documentId() + "-retry-" + (retryCount + 1));
        try {
            Message outbound = messageConverter.toMessage(message, new MessageProperties());
            outbound = retryMessage(retryCount + 1).postProcessMessage(outbound);
            rabbitTemplate.send("", retryQueue, outbound, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                confirmationTimeout.toMillis(),
                TimeUnit.MILLISECONDS
            );
            if (confirm == null || !confirm.isAck()) {
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            channel.basicAck(deliveryTag, false);
            log.atInfo().addKeyValue("documentId", message.documentId())
                .addKeyValue("retryCount", retryCount + 1)
                .log("document storage retry scheduled");
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, true);
            log.atWarn().addKeyValue("documentId", message.documentId()).setCause(exception)
                .log("document storage retry publication failed; delivery requeued");
        }
    }

    private MessagePostProcessor retryMessage(int retryCount) {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType("application/json");
            properties.setHeader(RETRY_COUNT_HEADER, retryCount);
            return message;
        };
    }

    private int retryCount(MessageProperties properties) {
        Object header = properties.getHeaders().get(RETRY_COUNT_HEADER);
        if (header instanceof Number number) {
            return number.intValue();
        }
        if (header instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
