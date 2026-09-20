package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentPublicationException;
import com.dockflow.dockflow.document.application.DocumentStorageRequestPublisher;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentStorageRequestPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldPublishOnlyTheSixPersistentJsonFieldsAfterAnAck() {
        UUID documentId = UUID.randomUUID();
        confirmRabbitPublication(true, null);

        publisher().publish(new DocumentStorageRequestPublisher.DocumentStorageRequested(
            1,
            documentId,
            "documents/" + documentId,
            "staging/" + documentId,
            123,
            "application/pdf"
        ));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
            org.mockito.ArgumentMatchers.eq(DocumentStorageRabbitTopology.EXCHANGE),
            org.mockito.ArgumentMatchers.eq(DocumentStorageRabbitTopology.ROUTING_KEY),
            message.capture(),
            any(CorrelationData.class)
        );

        String json = new String(message.getValue().getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("schemaVersion"));
        assertTrue(json.contains(documentId.toString()));
        assertTrue(json.contains("contentReference"));
        assertTrue(json.contains("sizeBytes"));
        assertTrue(json.contains("contentType"));
        assertFalse(json.contains("InputStream"));
        assertEquals(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT,
            message.getValue().getMessageProperties().getDeliveryMode());
    }

    @Test
    void shouldFailWhenRabbitRejectsThePublication() {
        UUID documentId = UUID.randomUUID();
        confirmRabbitPublication(false, "exchange rejected");

        assertThrows(DocumentPublicationException.class, () -> publisher().publish(
            new DocumentStorageRequestPublisher.DocumentStorageRequested(
                1,
                documentId,
                "documents/" + documentId,
                "staging/" + documentId,
                123,
                "application/pdf"
            )
        ));
    }

    private RabbitMqDocumentStorageRequestPublisher publisher() {
        return new RabbitMqDocumentStorageRequestPublisher(
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
