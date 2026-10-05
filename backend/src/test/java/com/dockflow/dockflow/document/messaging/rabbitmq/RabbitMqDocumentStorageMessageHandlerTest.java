package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqDocumentStorageMessageHandlerTest {

    private static final String RETRY_COUNT_HEADER = "x-docflow-retry-count";

    @Mock
    private DocumentStorageMessageConsumer consumer;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    @Test
    void shouldAcknowledgeOnlyAfterTheConsumerSucceeds() throws Exception {
        DocumentStorageRequestedMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentStorageMessageConsumer.ProcessingResult.acknowledged(message.documentId())
        );

        Message delivery = delivery(11);
        handler().handle(message, delivery, channel);

        verify(channel).basicAck(11, false);
        verify(channel, never()).basicReject(11, false);
        verify(channel, never()).basicNack(11, false, true);
        verifyNoRetryPublication();
    }

    @Test
    void shouldSendInvalidMessagesDirectlyToTheDeadLetterRoute() throws Exception {
        DocumentStorageRequestedMessage invalid = message();
        when(consumer.process(invalid)).thenThrow(
            new DocumentStorageMessageValidationException("schema is invalid")
        );

        handler().handle(invalid, delivery(12), channel);

        verify(channel).basicReject(12, false);
        verify(channel, never()).basicAck(12, false);
        verifyNoRetryPublication();
    }

    @Test
    void shouldPublishTheFirstRetryThenAcknowledgeTheOriginalDelivery() throws Exception {
        DocumentStorageRequestedMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentStorageMessageConsumer.ProcessingResult.retry(message.documentId())
        );
        confirmRabbitPublication();

        handler().handle(message, delivery(13), channel);

        ArgumentCaptor<String> queue = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> published = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), queue.capture(), published.capture(), any(CorrelationData.class));
        assertEquals(DocumentStorageRabbitTopology.RETRY_QUEUES.get(0).name(), queue.getValue());
        assertEquals(1, published.getValue().getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER));
        verify(channel).basicAck(13, false);
        verify(channel, never()).basicReject(13, false);
    }

    @Test
    void shouldSendTheDeliveryToDeadLetterAfterThreeRetries() throws Exception {
        DocumentStorageRequestedMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentStorageMessageConsumer.ProcessingResult.retry(message.documentId())
        );
        Message delivery = delivery(14);
        delivery.getMessageProperties().setHeader(RETRY_COUNT_HEADER, 3);

        handler().handle(message, delivery, channel);

        verify(consumer).markFailedAfterPermanentFailure(message.documentId());
        verify(channel).basicReject(14, false);
        verifyNoRetryPublication();
    }

    @Test
    void shouldKeepUnknownResultProcessingWhenRetriesAreExhausted() throws Exception {
        DocumentStorageRequestedMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentStorageMessageConsumer.ProcessingResult.retryKeepingProcessing(message.documentId())
        );
        Message delivery = delivery(16);
        delivery.getMessageProperties().setHeader(RETRY_COUNT_HEADER, 3);

        handler().handle(message, delivery, channel);

        verify(consumer, never()).markFailedAfterPermanentFailure(any());
        verify(channel).basicReject(16, false);
        verifyNoRetryPublication();
    }

    @Test
    void shouldUseEachApprovedRetryQueueBeforeDeadLettering() throws Exception {
        DocumentStorageRequestedMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentStorageMessageConsumer.ProcessingResult.retry(message.documentId())
        );
        confirmRabbitPublication();

        for (int retryCount = 0; retryCount < DocumentStorageRabbitTopology.RETRY_QUEUES.size(); retryCount++) {
            Message delivery = delivery(20 + retryCount);
            delivery.getMessageProperties().setHeader(RETRY_COUNT_HEADER, retryCount);

            handler().handle(message, delivery, channel);

            ArgumentCaptor<String> queue = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Message> published = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(eq(""), queue.capture(), published.capture(), any(CorrelationData.class));
            assertEquals(DocumentStorageRabbitTopology.RETRY_QUEUES.get(retryCount).name(), queue.getValue());
            assertEquals(retryCount + 1,
                published.getValue().getMessageProperties().getHeaders().get(RETRY_COUNT_HEADER));
            verify(channel).basicAck(20 + retryCount, false);

            org.mockito.Mockito.clearInvocations(rabbitTemplate, channel);
        }
    }

    @Test
    void shouldRequeueTheOriginalWhenRetryPublicationIsNotConfirmed() throws Exception {
        DocumentStorageRequestedMessage message = message();
        when(consumer.process(message)).thenReturn(
            DocumentStorageMessageConsumer.ProcessingResult.retry(message.documentId())
        );
        doThrow(new RuntimeException("broker unavailable"))
            .when(rabbitTemplate)
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        handler().handle(message, delivery(15), channel);

        verify(channel).basicNack(15, false, true);
        verify(channel, never()).basicAck(15, false);
    }

    private RabbitMqDocumentStorageMessageHandler handler() {
        return new RabbitMqDocumentStorageMessageHandler(consumer, rabbitTemplate);
    }

    private void confirmRabbitPublication() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate)
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private void verifyNoRetryPublication() {
        verify(rabbitTemplate, never())
            .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private Message delivery(long tag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(tag);
        return new Message(new byte[0], properties);
    }

    private DocumentStorageRequestedMessage message() {
        UUID id = UUID.randomUUID();
        return new DocumentStorageRequestedMessage(
            1,
            id,
            "documents/" + id,
            "staging/" + id,
            8,
            "application/pdf"
        );
    }

}
