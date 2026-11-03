package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.dockflow.dockflow.document.application.logging.ApplicationLogPersistenceService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** ACK/retry/DLQ boundary for application log persistence. */
public class RabbitMqApplicationLogMessageHandler {

    static final String RETRY_COUNT_HEADER = "x-docflow-application-log-retry-count";
    private static final Logger LOG = LoggerFactory.getLogger(RabbitMqApplicationLogMessageHandler.class);

    private final ApplicationLogMessageConsumer consumer;
    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Duration confirmationTimeout;

    public RabbitMqApplicationLogMessageHandler(
        ApplicationLogMessageConsumer consumer,
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        Duration confirmationTimeout
    ) {
        this.consumer = consumer;
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.confirmationTimeout = confirmationTimeout;
    }

    public void handle(ApplicationLogEvent event, Message delivery, Channel channel) throws IOException {
        long deliveryTag = delivery.getMessageProperties().getDeliveryTag();
        try {
            ApplicationLogPersistenceService.PersistenceResult result = consumer.process(event);
            channel.basicAck(deliveryTag, false);
            if (result == ApplicationLogPersistenceService.PersistenceResult.DUPLICATE) {
                LOG.atDebug().addKeyValue("eventId", event.eventId()).log("duplicate application log acknowledged");
            }
        } catch (ApplicationLogMessageValidationException exception) {
            LOG.atWarn().log("invalid application log sent to dead letter");
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException exception) {
            routeRetryOrDeadLetter(event, delivery, channel, exception);
        }
    }

    private void routeRetryOrDeadLetter(
        ApplicationLogEvent event,
        Message delivery,
        Channel channel,
        RuntimeException failure
    ) throws IOException {
        int retryCount = retryCount(delivery.getMessageProperties());
        if (retryCount >= ApplicationLogRabbitTopology.RETRY_QUEUES.size()) {
            LOG.atError().addKeyValue("eventId", event == null ? null : event.eventId())
                .log("application log persistence retries exhausted; sent to dead letter");
            channel.basicReject(delivery.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        String retryQueue = ApplicationLogRabbitTopology.RETRY_QUEUES.get(retryCount).name();
        CorrelationData correlation = new CorrelationData(
            "application-log-" + (event == null ? "unknown" : event.eventId()) + "-retry-" + (retryCount + 1)
        );
        try {
            Message outbound = messageConverter.toMessage(event, new MessageProperties());
            MessageProperties properties = outbound.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType("application/json");
            properties.setHeader(RETRY_COUNT_HEADER, retryCount + 1);
            rabbitTemplate.send("", retryQueue, outbound, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                confirmationTimeout.toMillis(), TimeUnit.MILLISECONDS
            );
            if (confirm == null || !confirm.isAck()) {
                channel.basicNack(delivery.getMessageProperties().getDeliveryTag(), false, true);
                return;
            }
            channel.basicAck(delivery.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception retryFailure) {
            channel.basicNack(delivery.getMessageProperties().getDeliveryTag(), false, true);
            LOG.atWarn().addKeyValue("eventId", event == null ? null : event.eventId())
                .log("application log retry publication failed; delivery requeued");
        }
    }

    private int retryCount(MessageProperties properties) {
        Object header = properties.getHeaders().get(RETRY_COUNT_HEADER);
        if (header instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        if (header instanceof String value) {
            try {
                return Math.max(Integer.parseInt(value), 0);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
