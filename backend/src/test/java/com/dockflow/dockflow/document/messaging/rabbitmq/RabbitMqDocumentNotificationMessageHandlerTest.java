package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentNotificationMessageHandlerTest {

    @Mock
    private DocumentNotificationMessageConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void acknowledgesAfterSuccessfulDispatch() throws IOException {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(0);
        when(consumer.process(event)).thenReturn(
            DocumentNotificationMessageConsumer.ProcessingResult.acknowledged(event.eventId())
        );

        handler().handle(event, delivery(31), channel);

        verify(channel).basicAck(31, false);
        verify(channel, never()).basicReject(31, false);
    }

    @Test
    void rejectsUndeliverableEventsWithoutRequeue() throws IOException {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(1);
        when(consumer.process(event)).thenReturn(
            DocumentNotificationMessageConsumer.ProcessingResult.deadLetter(event.eventId())
        );

        handler().handle(event, delivery(32), channel);

        verify(channel).basicReject(32, false);
        verify(channel, never()).basicAck(32, false);
    }

    @Test
    void rejectsInvalidEventsWithoutRequeue() throws IOException {
        when(consumer.process(null)).thenThrow(
            new DocumentNotificationMessageValidationException("invalid event")
        );

        handler().handle(null, delivery(33), channel);

        verify(channel).basicReject(33, false);
        verify(channel, never()).basicAck(33, false);
    }

    private RabbitMqDocumentNotificationMessageHandler handler() {
        return new RabbitMqDocumentNotificationMessageHandler(consumer);
    }

    private Message delivery(long tag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(tag);
        return new Message(new byte[0], properties);
    }
}
