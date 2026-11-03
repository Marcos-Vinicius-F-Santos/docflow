package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentEmailMessageHandlerTest {

    @Mock
    private DocumentEmailMessageConsumer consumer;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    @Test
    void acknowledgesAfterEmailDelivery() throws IOException {
        DocumentEmailNotificationMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentEmailMessageConsumer.ProcessingResult.acknowledged(message.event().eventId())
        );

        handler().handle(message, delivery(41, null), channel);

        verify(channel).basicAck(41, false);
        verify(channel, never()).basicReject(41, false);
    }

    @Test
    void publishesTheFirstRetryBeforeAcknowledgingTheOriginal() throws IOException {
        DocumentEmailNotificationMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentEmailMessageConsumer.ProcessingResult.transientFailure(message.event().eventId())
        );
        confirmRetryPublication(true, null);

        handler().handle(message, delivery(42, null), channel);

        ArgumentCaptor<Message> outbound = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
            eq(""),
            eq(DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.get(0).name()),
            outbound.capture(),
            any(CorrelationData.class)
        );
        assertEquals(1, outbound.getValue().getMessageProperties().getHeaders()
            .get(RabbitMqDocumentEmailMessageHandler.RETRY_COUNT_HEADER));
        assertEquals(MessageDeliveryMode.PERSISTENT, outbound.getValue().getMessageProperties().getDeliveryMode());
        verify(channel).basicAck(42, false);
    }

    @Test
    void requeuesTheOriginalWhenRetryPublicationIsNotConfirmed() throws IOException {
        DocumentEmailNotificationMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentEmailMessageConsumer.ProcessingResult.transientFailure(message.event().eventId())
        );
        confirmRetryPublication(false, "broker unavailable");

        handler().handle(message, delivery(43, null), channel);

        verify(channel).basicNack(43, false, true);
        verify(channel, never()).basicAck(43, false);
    }

    @Test
    void sendsTheMessageToDeadLetterAfterThreeRetries() throws IOException {
        DocumentEmailNotificationMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentEmailMessageConsumer.ProcessingResult.transientFailure(message.event().eventId())
        );

        handler().handle(
            message,
            delivery(44, DocumentNotificationRabbitTopology.EMAIL_RETRY_QUEUES.size()),
            channel
        );

        verify(channel).basicReject(44, false);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void publishesAllThreeRetriesBeforeDeadLetterIsAllowed() throws IOException {
        DocumentEmailNotificationMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentEmailMessageConsumer.ProcessingResult.transientFailure(message.event().eventId())
        );
        confirmRetryPublication(true, null);

        handler().handle(message, delivery(46, 0), channel);
        handler().handle(message, delivery(47, 1), channel);
        handler().handle(message, delivery(48, 2), channel);

        verify(channel).basicAck(46, false);
        verify(channel).basicAck(47, false);
        verify(channel).basicAck(48, false);
        verify(rabbitTemplate, org.mockito.Mockito.times(3)).send(
            anyString(),
            anyString(),
            any(Message.class),
            any(CorrelationData.class)
        );
    }

    @Test
    void sendsPermanentFailuresDirectlyToDeadLetter() throws IOException {
        DocumentEmailNotificationMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentEmailMessageConsumer.ProcessingResult.permanentFailure(message.event().eventId())
        );

        handler().handle(message, delivery(45, null), channel);

        verify(channel).basicReject(45, false);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private RabbitMqDocumentEmailMessageHandler handler() {
        return new RabbitMqDocumentEmailMessageHandler(
            consumer,
            rabbitTemplate,
            new Jackson2JsonMessageConverter(),
            Duration.ofSeconds(1)
        );
    }

    private Message delivery(long tag, Integer retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(tag);
        if (retryCount != null) {
            properties.setHeader(RabbitMqDocumentEmailMessageHandler.RETRY_COUNT_HEADER, retryCount);
        }
        return new Message(new byte[0], properties);
    }

    private DocumentEmailNotificationMessage message() {
        return new DocumentEmailNotificationMessage(
            DocumentEmailNotificationMessage.SCHEMA_VERSION,
            DocumentNotificationTestFixtures.lifecycleEvents().get(1),
            "recipient@example.com"
        );
    }

    private void confirmRetryPublication(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate)
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
