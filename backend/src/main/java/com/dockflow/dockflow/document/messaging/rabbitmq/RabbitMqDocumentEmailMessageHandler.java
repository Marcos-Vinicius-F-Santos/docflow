package com.dockflow.dockflow.document.messaging.rabbitmq;

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

/** RabbitMQ boundary for e-mail retries and dead-lettering. */
public class RabbitMqDocumentEmailMessageHandler {

    static final String RETRY_COUNT_HEADER = "x-docflow-email-retry-count";
    private static final Logger log = LoggerFactory.getLogger(RabbitMqDocumentEmailMessageHandler.class);

    private final DocumentEmailMessageConsumer consumer;
    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Duration confirmationTimeout;

    public RabbitMqDocumentEmailMessageHandler(
        DocumentEmailMessageConsumer consumer,
        RabbitTemplate rabbitTemplate
    ) {
        this(consumer, rabbitTemplate, new Jackson2JsonMessageConverter(), DocumentStorageRabbitTopology.PROCESSING_TIMEOUT);
    }

    public RabbitMqDocumentEmailMessageHandler(
        DocumentEmailMessageConsumer consumer,
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        Duration confirmationTimeout
    ) {
        this.consumer = consumer;
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.confirmationTimeout = confirmationTimeout;
    }

    public void handle(DocumentEmailNotificationMessage message, Message delivery, Channel channel) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        UUID eventId = message == null || message.event() == null ? null : message.event().eventId();
        try {
            DocumentEmailMessageConsumer.ProcessingResult result = consumer.process(message);
            switch (result.disposition()) {
                case ACKNOWLEDGE -> channel.basicAck(deliveryTag, false);
                case DEAD_LETTER -> channel.basicReject(deliveryTag, false);
                case RETRY -> routeRetry(message, delivery, channel, eventId);
            }
        } catch (DocumentEmailMessageValidationException exception) {
            log.atWarn().addKeyValue("eventId", eventId).setCause(exception)
                .log("invalid e-mail command sent to dead letter");
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.atWarn().addKeyValue("eventId", eventId).setCause(exception)
                .log("e-mail command failed; sent to dead letter");
            channel.basicReject(deliveryTag, false);
        }
    }

    private void routeRetry(
        DocumentEmailNotificationMessage message,
        Message delivery,
        Channel channel,
        UUID eventId
    ) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        int retryCount = retryCount(delivery.getMessageProperties());
        if (retryCount >= DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.size()) {
            channel.basicReject(deliveryTag, false);
            log.atWarn().addKeyValue("eventId", eventId).log("e-mail retries exhausted; sent to dead letter");
            return;
        }

        String retryQueue = DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.get(retryCount).name();
        CorrelationData correlation = new CorrelationData("email-" + eventId + "-retry-" + (retryCount + 1));
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
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, true);
            log.atWarn().addKeyValue("eventId", eventId).setCause(exception)
                .log("e-mail retry publication failed; delivery requeued");
        }
    }

    private MessagePostProcessor retryMessage(int retryCount) {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType(DocumentNotificationRabbitTopology.JSON_CONTENT_TYPE);
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
