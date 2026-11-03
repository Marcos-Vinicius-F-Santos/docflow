package com.dockflow.dockflow.document.messaging.rabbitmq;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentLifecycleEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesEachLifecycleTypeWithItsApprovedRoutingKeyAndPersistentJson() {
        List<String> routingKeys = List.of(
            DocumentNotificationRabbitTopology.PROCESSING_STARTED_ROUTING_KEY,
            DocumentNotificationRabbitTopology.COMPLETED_ROUTING_KEY,
            DocumentNotificationRabbitTopology.FAILED_ROUTING_KEY,
            DocumentNotificationRabbitTopology.DELETION_WARNING_ROUTING_KEY,
            DocumentNotificationRabbitTopology.DELETED_ROUTING_KEY
        );
        confirmRabbitPublication();
        RabbitMqDocumentLifecycleEventPublisher publisher = publisher();

        for (DocumentLifecycleEvent event : DocumentNotificationTestFixtures.lifecycleEvents()) {
            publisher.publish(event);
        }

        ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, org.mockito.Mockito.times(5)).send(
            org.mockito.ArgumentMatchers.eq(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE),
            routingKey.capture(),
            message.capture(),
            any(CorrelationData.class)
        );

        assertEquals(routingKeys, routingKey.getAllValues());
        for (Message outbound : message.getAllValues()) {
            assertEquals(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT,
                outbound.getMessageProperties().getDeliveryMode());
            assertEquals(DocumentNotificationRabbitTopology.JSON_CONTENT_TYPE,
                outbound.getMessageProperties().getContentType());
            String json = new String(outbound.getBody(), StandardCharsets.UTF_8);
            assertTrue(json.contains("schemaVersion"));
            assertTrue(json.contains("documentId"));
        }
    }

    @Test
    void schedulesDeletionWarningThroughTheDelayRoute() {
        confirmRabbitPublication();

        publisher().scheduleDeletionWarning(DocumentNotificationTestFixtures.lifecycleEvents().get(3));

        verify(rabbitTemplate).send(
            org.mockito.ArgumentMatchers.eq(DocumentNotificationRabbitTopology.EVENTS_EXCHANGE),
            org.mockito.ArgumentMatchers.eq(DocumentNotificationRabbitTopology.DELETION_WARNING_SCHEDULED_ROUTING_KEY),
            any(Message.class),
            any(CorrelationData.class)
        );
    }

    private RabbitMqDocumentLifecycleEventPublisher publisher() {
        return new RabbitMqDocumentLifecycleEventPublisher(
            rabbitTemplate,
            new Jackson2JsonMessageConverter(),
            Duration.ofSeconds(1)
        );
    }

    private void confirmRabbitPublication() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate)
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
