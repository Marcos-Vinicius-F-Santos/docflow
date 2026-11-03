package com.dockflow.dockflow.document.messaging.rabbitmq;

import com.dockflow.dockflow.document.adapter.out.email.EmailDeliveryException;
import com.dockflow.dockflow.document.port.out.email.DocumentEmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentEmailMessageConsumerTest {

    @Mock
    private DocumentEmailSender sender;

    @Test
    void acknowledgesASuccessfulSendAndDeduplicatesTheEvent() {
        DocumentEmailNotificationMessage message = message();
        DocumentEmailMessageConsumer consumer = new DocumentEmailMessageConsumer(Optional.of(sender));

        DocumentEmailMessageConsumer.ProcessingResult first = consumer.process(message);
        DocumentEmailMessageConsumer.ProcessingResult duplicate = consumer.process(message);

        assertEquals(DocumentEmailMessageConsumer.Disposition.ACKNOWLEDGE, first.disposition());
        assertEquals(DocumentEmailMessageConsumer.Disposition.ACKNOWLEDGE, duplicate.disposition());
        verify(sender).send(message.event(), message.recipient());
    }

    @Test
    void classifiesTransientProviderFailuresForRetry() {
        DocumentEmailNotificationMessage message = message();
        doThrow(new EmailDeliveryException(EmailDeliveryException.FailureType.TRANSIENT, "timeout"))
            .when(sender).send(message.event(), message.recipient());
        DocumentEmailMessageConsumer consumer = new DocumentEmailMessageConsumer(Optional.of(sender));

        DocumentEmailMessageConsumer.ProcessingResult result = consumer.process(message);

        assertEquals(DocumentEmailMessageConsumer.Disposition.RETRY, result.disposition());
    }

    @Test
    void classifiesPermanentProviderFailuresForDeadLetter() {
        DocumentEmailNotificationMessage message = message();
        doThrow(new EmailDeliveryException(EmailDeliveryException.FailureType.PERMANENT, "invalid recipient"))
            .when(sender).send(message.event(), message.recipient());
        DocumentEmailMessageConsumer consumer = new DocumentEmailMessageConsumer(Optional.of(sender));

        DocumentEmailMessageConsumer.ProcessingResult result = consumer.process(message);

        assertEquals(DocumentEmailMessageConsumer.Disposition.DEAD_LETTER, result.disposition());
    }

    @Test
    void rejectsInvalidCommandsBeforeCallingTheProvider() {
        DocumentEmailMessageConsumer consumer = new DocumentEmailMessageConsumer(Optional.of(sender));

        assertThrows(DocumentEmailMessageValidationException.class, () -> consumer.process(null));
    }

    private DocumentEmailNotificationMessage message() {
        return new DocumentEmailNotificationMessage(
            DocumentEmailNotificationMessage.SCHEMA_VERSION,
            DocumentNotificationTestFixtures.lifecycleEvents().get(1),
            "recipient@example.com"
        );
    }
}
