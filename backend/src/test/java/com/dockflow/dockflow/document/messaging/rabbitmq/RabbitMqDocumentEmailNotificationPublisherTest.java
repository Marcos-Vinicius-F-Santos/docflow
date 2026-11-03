package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentPublicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentEmailNotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesTheEmailCommandToTheApprovedRouteAfterConfirmation() {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(1);
        confirm(true, null);

        publisher().dispatch(event);

        ArgumentCaptor<Message> outbound = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
            eq(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE),
            eq(DocumentNotificationRabbitTopology.EMAIL_ROUTING_KEY),
            outbound.capture(),
            any(CorrelationData.class)
        );
        String json = new String(outbound.getValue().getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("recipient@example.com"));
        assertTrue(json.contains(event.eventId().toString()));
        assertEquals(MessageDeliveryMode.PERSISTENT, outbound.getValue().getMessageProperties().getDeliveryMode());
    }

    @Test
    void failsTheChannelWhenRabbitDoesNotConfirmTheEmailCommand() {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(0);
        confirm(false, "exchange rejected");

        assertThrows(DocumentPublicationException.class, () -> publisher().dispatch(event));
    }

    private RabbitMqDocumentEmailNotificationPublisher publisher() {
        return new RabbitMqDocumentEmailNotificationPublisher(
            rabbitTemplate,
            new Jackson2JsonMessageConverter(),
            "recipient@example.com",
            Duration.ofSeconds(1)
        );
    }

    private void confirm(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate)
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
