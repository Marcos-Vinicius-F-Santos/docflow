package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.logging.ApplicationLogEvent;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** RabbitMQ publisher with durable messages and publisher-confirm enforcement. */
public class RabbitMqApplicationLogPublisher implements ApplicationLogPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;
    private final Duration confirmationTimeout;

    public RabbitMqApplicationLogPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        Duration confirmationTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageConverter = messageConverter;
        this.confirmationTimeout = confirmationTimeout;
    }

    @Override
    public void publish(ApplicationLogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        try {
            Message outbound = messageConverter.toMessage(event, new MessageProperties());
            MessageProperties properties = outbound.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType("application/json");
            properties.setHeader("schemaVersion", ApplicationLogRabbitTopology.MESSAGE_SCHEMA_VERSION);
            rabbitTemplate.send(
                ApplicationLogRabbitTopology.EXCHANGE,
                routingKey(event.level()),
                outbound,
                correlation
            );

            CorrelationData.Confirm confirm = correlation.getFuture().get(
                confirmationTimeout.toMillis(),
                TimeUnit.MILLISECONDS
            );
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "no publisher confirmation" : confirm.getReason();
                throw new ApplicationLogPublicationException("RabbitMQ did not confirm application log: " + reason);
            }
        } catch (ApplicationLogPublicationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApplicationLogPublicationException("Application log confirmation was interrupted", exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw new ApplicationLogPublicationException("Application log publication was not confirmed", exception);
        }
    }

    static String routingKey(ApplicationLogLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level cannot be null");
        }
        return switch (level) {
            case INFO -> ApplicationLogRabbitTopology.INFO_ROUTING_KEY;
            case WARN -> ApplicationLogRabbitTopology.WARN_ROUTING_KEY;
            case ERROR -> ApplicationLogRabbitTopology.ERROR_ROUTING_KEY;
        };
    }
}
