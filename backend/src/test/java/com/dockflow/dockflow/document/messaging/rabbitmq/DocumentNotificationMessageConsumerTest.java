package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.application.DocumentNotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentNotificationMessageConsumerTest {

    @Mock
    private DocumentNotificationDispatcher dispatcher;

    @Test
    void acknowledgesOnlyAfterDispatchAndDeduplicatesAReDelivery() {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(0);
        DocumentNotificationMessageConsumer consumer = new DocumentNotificationMessageConsumer(Optional.of(dispatcher));

        DocumentNotificationMessageConsumer.ProcessingResult first = consumer.process(event);
        DocumentNotificationMessageConsumer.ProcessingResult duplicate = consumer.process(event);

        assertEquals(DocumentNotificationMessageConsumer.Disposition.ACKNOWLEDGE, first.disposition());
        assertEquals(DocumentNotificationMessageConsumer.Disposition.ACKNOWLEDGE, duplicate.disposition());
        verify(dispatcher).dispatch(event);
    }

    @Test
    void sendsDispatchFailureToDeadLetterAndAllowsAControlledReDelivery() {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(1);
        doThrow(new IllegalStateException("channel unavailable")).doNothing().when(dispatcher).dispatch(event);
        DocumentNotificationMessageConsumer consumer = new DocumentNotificationMessageConsumer(Optional.of(dispatcher));

        DocumentNotificationMessageConsumer.ProcessingResult failed = consumer.process(event);
        DocumentNotificationMessageConsumer.ProcessingResult recovered = consumer.process(event);

        assertEquals(DocumentNotificationMessageConsumer.Disposition.DEAD_LETTER, failed.disposition());
        assertEquals(DocumentNotificationMessageConsumer.Disposition.ACKNOWLEDGE, recovered.disposition());
        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(event);
    }

    @Test
    void deadLettersWhenNoDispatcherIsAvailable() {
        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(2);
        DocumentNotificationMessageConsumer consumer = new DocumentNotificationMessageConsumer(Optional.empty());

        DocumentNotificationMessageConsumer.ProcessingResult result = consumer.process(event);

        assertEquals(DocumentNotificationMessageConsumer.Disposition.DEAD_LETTER, result.disposition());
        verify(dispatcher, never()).dispatch(event);
    }

    @Test
    void rejectsInvalidNotificationMessages() {
        DocumentNotificationMessageConsumer consumer = new DocumentNotificationMessageConsumer(Optional.of(dispatcher));

        assertThrows(
            DocumentNotificationMessageValidationException.class,
            () -> consumer.process(null)
        );
    }
}
