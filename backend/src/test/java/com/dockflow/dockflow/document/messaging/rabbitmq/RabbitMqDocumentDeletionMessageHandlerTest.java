package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentDeletionMessageHandlerTest {

    @Mock
    private DocumentDeletionMessageConsumer consumer;

    @Mock
    private Channel channel;

    @Test
    void shouldAckOnlyAfterDeletionIsConfirmed() throws Exception {
        DocumentDeletionScheduledMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentDeletionMessageConsumer.ProcessingResult.acknowledged(message.documentId())
        );

        handler().handle(message, delivery(21), channel);

        verify(channel).basicAck(21, false);
        verify(channel, never()).basicNack(21, false, false);
    }

    @Test
    void shouldNackWithoutRequeueWhenDeletionFails() throws Exception {
        DocumentDeletionScheduledMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentDeletionMessageConsumer.ProcessingResult.deadLetter(message.documentId())
        );

        handler().handle(message, delivery(22), channel);

        verify(channel).basicNack(22, false, false);
        verify(channel, never()).basicAck(22, false);
    }

    @Test
    void shouldNackInvalidMessagesWithoutRequeue() throws Exception {
        DocumentDeletionScheduledMessage message = message();
        when(consumer.process(message)).thenThrow(new DocumentDeletionMessageValidationException("invalid"));

        handler().handle(message, delivery(23), channel);

        verify(channel).basicNack(23, false, false);
        verify(channel, never()).basicAck(23, false);
    }

    private RabbitMqDocumentDeletionMessageHandler handler() {
        return new RabbitMqDocumentDeletionMessageHandler(consumer);
    }

    private Message delivery(long tag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(tag);
        return new Message(new byte[0], properties);
    }

    private DocumentDeletionScheduledMessage message() {
        return new DocumentDeletionScheduledMessage(
            DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
            UUID.randomUUID()
        );
    }
}
