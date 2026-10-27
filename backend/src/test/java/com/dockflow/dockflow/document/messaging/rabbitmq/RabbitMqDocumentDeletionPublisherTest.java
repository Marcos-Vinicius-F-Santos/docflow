package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentDeletionPublisher;
import com.dockflow.dockflow.document.application.DocumentPublicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentDeletionPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldPublishOnlyTheVersionAndDocumentIdAsPersistentJsonAfterAnAck() {
        UUID documentId = UUID.randomUUID();
        confirmRabbitPublication(true, null);

        publisher().publish(new DocumentDeletionPublisher.DocumentDeletionScheduled(
            DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
            documentId
        ));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
            eq(DocumentStorageRabbitTopology.DELETION_EXCHANGE),
            eq(DocumentStorageRabbitTopology.DELETION_SCHEDULED_ROUTING_KEY),
            message.capture(),
            any(CorrelationData.class)
        );

        String json = new String(message.getValue().getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("schemaVersion"));
        assertTrue(json.contains(documentId.toString()));
        assertFalse(json.contains("objectKey"));
        assertFalse(json.contains("staging"));
        assertEquals(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT,
            message.getValue().getMessageProperties().getDeliveryMode());
    }

    @Test
    void shouldFailWhenDeletionSchedulingIsNotConfirmed() {
        UUID documentId = UUID.randomUUID();
        confirmRabbitPublication(false, "exchange rejected");

        assertThrows(DocumentPublicationException.class, () -> publisher().publish(
            new DocumentDeletionPublisher.DocumentDeletionScheduled(
                DocumentStorageRabbitTopology.DELETION_MESSAGE_SCHEMA_VERSION,
                documentId
            )
        ));
    }

    private RabbitMqDocumentDeletionPublisher publisher() {
        return new RabbitMqDocumentDeletionPublisher(
            rabbitTemplate,
            new Jackson2JsonMessageConverter(),
            Duration.ofSeconds(1)
        );
    }

    private void confirmRabbitPublication(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate)
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
